package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path"
	"strconv"
	"time"
)

type FuzzerType int

const (
	RandomFuzzer FuzzerType = 0
	ModelFuzz    FuzzerType = 1
	TraceFuzzer  FuzzerType = 2
)

func (ft FuzzerType) String() string {
	switch ft {
	case ModelFuzz:
		return "modelfuzz"
	case RandomFuzzer:
		return "random"
	case TraceFuzzer:
		return "trace"
	default:
		return fmt.Sprintf("%d", int(ft))
	}
}

type FuzzerConfig struct {
	// TimeBudget			int
	Horizon    int
	Iterations int
	NumNodes   int
	// RecordPath			string
	LogLevel       string
	NetworkPort    int
	BaseWorkingDir string
	RatisDataDir   string

	MutationsPerTrace int
	SeedPopulation    int
	NumRequests       int
	NumCrashes        int
	MaxMessages       int
	ReseedFrequency   int
	RandomSeed        int

	ClusterConfig *ClusterConfig
	TLCPort       int
}

type Fuzzer struct {
	config        FuzzerConfig
	logger        *Logger
	network       *Network
	fuzzerType    FuzzerType
	scheduleQueue []*Trace
	stats         *Stats
	random        *rand.Rand
	guider        Guider
	mutator       Mutator
}

func NewFuzzer(config FuzzerConfig, fuzzerType FuzzerType) (*Fuzzer, error) {
	f := &Fuzzer{
		config:        config,
		logger:        NewLogger(),
		fuzzerType:    fuzzerType,
		scheduleQueue: make([]*Trace, 0),
		stats: &Stats{
			Coverages:     make([]int, 0),
			RandomTraces:  0,
			MutatedTraces: 0,
			// TimeStamps: make([]time.Duration, 0),
		},
		random: rand.New(rand.NewSource(int64(config.RandomSeed))),
	}
	f.logger.SetLevel(config.LogLevel)

	if _, err := os.Stat(config.BaseWorkingDir); err == nil {
		os.RemoveAll(config.BaseWorkingDir)
	}
	os.MkdirAll(config.BaseWorkingDir, 0777)

	ctx, _ := context.WithCancel(context.Background())
	f.network = NewNetwork(ctx, config.NetworkPort, f.logger.With(LogParams{"type": "network"}))
	addr := fmt.Sprintf("localhost:%d", config.TLCPort)
	f.guider = NewGuider(fuzzerType, addr, config.BaseWorkingDir)
	f.mutator = CombineMutators(NewSwapCrashNodeMutator(1, f.random), NewSwapNodeMutator(20, f.random), NewSwapMaxMessagesMutator(20, f.random))
	f.logger.Debug("Initialized fuzzer")

	return f, nil
}

func (f *Fuzzer) Reset() {
	f.guider.Reset()
}

func (f *Fuzzer) Run() {
	f.logger.Debug("Running fuzzer...")
	// iter := 0
	for iter := 0; iter < f.config.Iterations; iter++ { // fuzzerStart := time.Now(); time.Since(fuzzerStart) < time.Duration(f.config.TimeBudget) * time.Minute;  { // TODO - Back to hour
		if iter%10 == 0 {
			f.logger.Info(strconv.Itoa(iter))
		}
		f.logger.Debug("Seeding.")
		if iter%f.config.ReseedFrequency == 0 && f.fuzzerType != RandomFuzzer {
			f.scheduleQueue = make([]*Trace, 0)
			for i := 0; i < f.config.SeedPopulation; i++ {
				f.scheduleQueue = append(f.scheduleQueue, f.GenerateRandom())
			}
		}

		// Set up directory
		workDir := path.Join(f.config.BaseWorkingDir, strconv.Itoa(iter))
		if _, err := os.Stat(workDir); err == nil {
			os.RemoveAll(workDir)
		}
		os.MkdirAll(workDir, 0777)

		// Start network
		f.network.Start()

		// Start cluster
		f.config.ClusterConfig.WorkDir = path.Join(workDir, "cluster")
		f.config.ClusterConfig.RatisDataDir = f.config.RatisDataDir
		f.config.ClusterConfig.ClusterID = iter
		f.config.ClusterConfig.SchedulerPort = f.config.NetworkPort
		cluster := NewCluster(f.config.ClusterConfig, f.logger.With(LogParams{"type": "cluster"}))
		cluster.Start()

		// Get schedule
		var schedule *Trace
		mutated := true
		if f.fuzzerType == RandomFuzzer {
			schedule = f.GenerateRandom()
			mutated = false
		} else {
			if len(f.scheduleQueue) > 0 {
				f.logger.Debug("Taking schedule from schedule queue")
				schedule = f.scheduleQueue[0]
				f.scheduleQueue = f.scheduleQueue[1:]
			} else {
				f.logger.Debug("Generating new random schedule")
				schedule = f.GenerateRandom()
				mutated = false
			}
		}

		crashPoints := make(map[int]string)
		scheduleFromNode := make([]string, f.config.Horizon)
		scheduleToNode := make([]string, f.config.Horizon)
		scheduleMaxMessages := make([]int, f.config.Horizon)
		clientRequests := make(map[int]string)

		for _, ch := range schedule.Choices {
			switch ch.Type {
			case "Node":
				scheduleFromNode[ch.Step] = ch.From
				scheduleToNode[ch.Step] = ch.To
				scheduleMaxMessages[ch.Step] = ch.MaxMessages
			case "Crash":
				crashPoints[ch.Step] = ch.Node
			case "ClientRequest":
				clientRequests[ch.Step] = ch.Op
			}
		}

		crashCount := 0
		requestCount := 0
		start := time.Now()
		for !f.network.WaitForNodes(f.config.NumNodes) {
			if time.Since(start).Seconds() > 10 {
				// Stop and reset cluster
				logs := cluster.GetLogs()
				cluster.Destroy()

				// Save logs
				filePath := workDir + "/logs.log"
				file, err := os.Create(filePath)
				if err != nil {
					return
				}
				defer file.Close()
				writer := bufio.NewWriter(file)
				writer.WriteString(logs)
				writer.Flush()

				// Stop and reset network
				f.network.Reset()
			}

			time.Sleep(1 * time.Millisecond)
		}

		f.logger.Debug("Fuzzer setup complete.")
		time.Sleep(3 * time.Second)

		for step := 0; step < f.config.Horizon; step++ {

			f.logger.Debug(strconv.Itoa(step))
			crashNode, ok := crashPoints[step]
			if ok {
				n, _ := strconv.Atoi(crashNode)
				f.logger.Debug("Crashing node...")
				node, ok := cluster.GetNode(crashNode)
				if ok {
					node.Stop()
					f.network.AddEvent(Event{
						Name: "Remove",
						Node: crashNode,
						Params: map[string]interface{}{
							"i": n,
						},
					})
				}
				crashCount++
				// node.config.InterceptorPort += ((iter % 2) * f.config.NumCrashes + crashCount + f.config.NumNodes)

				node.Start()
				f.network.AddEvent(Event{
					Name: "Add",
					Node: crashNode,
					Params: map[string]interface{}{
						"i": n,
					},
				})
			}

			f.network.Schedule(scheduleFromNode[step], scheduleToNode[step], scheduleMaxMessages[step])

			op, ok := clientRequests[step]
			if ok {
				f.logger.Debug("Sending request: " + op)
				cluster.SendRequest()
				f.network.AddClientRequestEvent(requestCount)
				requestCount++
			}

			time.Sleep(50 * time.Millisecond)
		}

		// Stop and reset cluster
		logs := cluster.GetLogs()
		cluster.Destroy()

		// Save logs
		filePath := workDir + "/logs.log"
		file, err := os.Create(filePath)
		if err != nil {
			return
		}
		defer file.Close()
		writer := bufio.NewWriter(file)
		writer.WriteString(logs)
		writer.Flush()

		// Get event trace
		eventTrace := f.network.GetEventTrace()

		// Stop and reset network
		f.network.Reset()

		// Get coverage
		var newStates bool
		var weight int
		// for _, event := range eventTrace.Events {
		// 	f.logger.Info(event.Name)
		// }
		if f.guider != nil {
			newStates, weight = f.guider.Check("states", schedule, eventTrace, true)
			f.logger.Debug("Found " + strconv.Itoa(weight) + " new states")
		}
		if newStates && f.fuzzerType != RandomFuzzer {
			mutatedTraces := make([]*Trace, 0)
			for i := 0; i < weight*f.config.MutationsPerTrace; i++ {
				// Mutate and update schedule queue
				newTrace, ok := f.mutator.Mutate(schedule, eventTrace)
				if ok {
					mutatedTraces = append(mutatedTraces, newTrace.Copy())
				}
			}
			f.scheduleQueue = append(f.scheduleQueue, mutatedTraces...)
		}

		// Update stats
		if mutated {
			f.stats.MutatedTraces++
		} else {
			f.stats.RandomTraces++
		}
		f.stats.Coverages = append(f.stats.Coverages, f.guider.Coverage())
		// f.stats.TimeStamps = append(f.stats.TimeStamps, time.Since(fuzzerStart))

		if iter%5 == 0 {
			// Save stats
			filePath := path.Join(f.config.BaseWorkingDir, "stats.json")
			dataB, err := json.MarshalIndent(f.stats, "", "\t")
			if err != nil {
				return
			}

			if _, err := os.Stat(filePath); err == nil {
				os.Remove(filePath)
			}

			file, err := os.Create(filePath)
			if err != nil {
				return
			}
			defer file.Close()
			writer := bufio.NewWriter(file)
			writer.Write(dataB)
			writer.Flush()
		}
	}

	// Save stats
	filePath := path.Join(f.config.BaseWorkingDir, "stats.json")
	dataB, err := json.MarshalIndent(f.stats, "", "\t")
	if err != nil {
		return
	}

	if _, err := os.Stat(filePath); err == nil {
		os.Remove(filePath)
	}

	file, err := os.Create(filePath)
	if err != nil {
		return
	}
	defer file.Close()
	writer := bufio.NewWriter(file)
	writer.Write(dataB)
	writer.Flush()

	//  --- store & print paths ---
	pathsFile := path.Join(f.config.BaseWorkingDir, "paths.json")
	if err := f.guider.DumpPaths(pathsFile); err != nil {
		f.logger.Error(fmt.Sprintf("failed to write paths: %v", err))
	} else {
		f.logger.Info(fmt.Sprintf("state paths written to %s", pathsFile))
		for i, p := range f.guider.Paths() {
			f.logger.Info(fmt.Sprintf("Path %d: %v", i, p))
		}
	}
}

func (f *Fuzzer) GenerateRandom() *Trace {
	trace := NewTrace()
	for i := 0; i < f.config.Horizon; i++ {
		fromIdx := f.random.Intn(f.config.NumNodes) + 1
		toIdx := f.random.Intn(f.config.NumNodes) + 1
		trace.Add(Choice{
			Type:        "Node",
			Step:        i,
			From:        strconv.Itoa(fromIdx),
			To:          strconv.Itoa(toIdx),
			MaxMessages: f.random.Intn(f.config.MaxMessages + 1),
		})
	}
	choices := make([]int, f.config.Horizon)
	for i := 0; i < f.config.Horizon; i++ {
		choices[i] = i
	}
	for _, c := range sample(choices, f.config.NumCrashes, f.random) {
		idx := f.random.Intn(f.config.NumNodes) + 1
		trace.Add(Choice{
			Type: "Crash",
			Node: strconv.Itoa(idx),
			Step: c,
		})

		// s := sample(intRange(c, f.config.InitialHorizon), 1, f.random)[0]
		// trace.Add(Choice{
		// 	Type: "Start",
		// 	Node: idx,
		// 	Step: s,
		// })
	}

	for _, req := range sample(choices, f.config.NumRequests, f.random) {
		trace.Add(Choice{
			Type: "ClientRequest",
			Op:   "write",
			Step: req,
		})
	}
	return trace
}

func (f *Fuzzer) Cleanup() {}
