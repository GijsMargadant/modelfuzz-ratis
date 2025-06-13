package main

import (
	"encoding/binary"
	"encoding/json"
	"hash/fnv"
	"iter"
	"os"
	"path/filepath"
	"strings"
)

type KPathCoverageGuider struct {
	tlcClient  *TLCClient
	logger     *Logger
	workingDir string
	k          int

	uniqueKPaths map[uint64]string
	uniqueStates map[int64]string
}

var _ Guider = &KPathCoverageGuider{}

func NewKPathCoverageGuider(tlcAddr string, workingDir string, logger *Logger, k int) *KPathCoverageGuider {
	return &KPathCoverageGuider{
		tlcClient:  NewTLCClient(tlcAddr),
		logger:     logger,
		workingDir: workingDir,
		k:          k,

		uniqueKPaths: make(map[uint64]string, 0),
		uniqueStates: make(map[int64]string, 0),
	}
}

func (g *KPathCoverageGuider) Check(iter string, trace *Trace, eventTrace *EventTrace, record bool) (bool, int) {
	tlcStates, err := g.tlcClient.SendTrace(eventTrace)
	if err != nil {
		panic(err.Error())
	}

	_ = UpdateUniqueStates(g.uniqueStates, tlcStates)
	newStates := updateUniqueKPaths(g.uniqueKPaths, tlcStates, g.k)

	return newStates != 0, newStates
}

func UpdateUniqueStates(uniqueStates map[int64]string, states []TLCState) int {
	newCount := 0
	for _, state := range states {
		if _, ok := uniqueStates[state.Key]; !ok {
			uniqueStates[state.Key] = state.Repr
			newCount += 1
		}
	}
	return newCount
}

func updateUniqueKPaths(uniqueKPaths map[uint64]string, states []TLCState, k int) int {
	newCount := 0
	for kPath := range SlidingWindow(states, k) {
		key := HashTLCStates(kPath)
		if _, ok := uniqueKPaths[key]; !ok {
			uniqueKPaths[key] = TLCStatesToString(kPath)
			newCount += 1
		}
	}
	return newCount
}

func SlidingWindow(states []TLCState, k int) iter.Seq[[]TLCState] {
	return func(yield func([]TLCState) bool) {
		if k > len(states) {
			yield(states)
			return
		}
		for i := 0; i <= len(states)-k; i++ {
			if !yield(states[i : i+k]) {
				return
			}
		}
	}
}

func HashTLCStates(states []TLCState) uint64 {
	h := fnv.New64a()
	buff := make([]byte, 8)
	for _, state := range states {
		binary.LittleEndian.PutUint64(buff, uint64(state.Key))
		h.Write(buff)
	}
	return h.Sum64()
}

func TLCStatesToString(states []TLCState) string {
	reprs := make([]string, len(states))
	for i, state := range states {
		reprs[i] = state.Repr
	}
	return "[" + strings.Join(reprs, ",") + "]"
}

func (g *KPathCoverageGuider) Coverage() int {
	AppendKPathCoverageToFile(g.workingDir, len(g.uniqueKPaths))
	AppendNewStatesToFile(g.workingDir, g.uniqueStates)
	return len(g.uniqueStates)
}

type KPathStats struct {
	Coverages []int `json:"coverages"`
}

func AppendKPathCoverageToFile(dir string, coverage int) {
	file := filepath.Join(dir, "kpath_stats.json")
	var stats KPathStats

	data, err := os.ReadFile(file)
	if err == nil {
		_ = json.Unmarshal(data, &stats)
	}

	stats.Coverages = append(stats.Coverages, coverage)

	newData, err := json.MarshalIndent(stats, "", "  ")
	if err != nil {
		panic("failed to marshal updated kpath_stats.json: " + err.Error())
	}

	if err := os.WriteFile(file, newData, 0644); err != nil {
		panic("failed to write kpath_stats.json: " + err.Error())
	}
}

type UniqueStates struct {
	States [][]TLCState `json:"states"`
}

func AppendNewStatesToFile(dir string, uniqueStates map[int64]string) {
	file := filepath.Join(dir, "unique_states.json")
	var existing UniqueStates

	data, err := os.ReadFile(file)
	if err == nil {
		_ = json.Unmarshal(data, &existing)
	}

	known := make(map[int64]bool)
	for _, stateList := range existing.States {
		for _, state := range stateList {
			known[state.Key] = true
		}
	}

	var newStates []TLCState
	for key, repr := range uniqueStates {
		if !known[key] {
			newStates = append(newStates, TLCState{Key: key, Repr: repr})
		}
	}

	existing.States = append(existing.States, newStates)

	newData, err := json.MarshalIndent(existing, "", "  ")
	if err != nil {
		panic("failed to marshal updated unique_states.json: " + err.Error())
	}

	if err := os.WriteFile(file, newData, 0644); err != nil {
		panic("failed to write unique_states.json: " + err.Error())
	}
}

func (g *KPathCoverageGuider) Reset() {
	g.uniqueKPaths = make(map[uint64]string, 0)
	g.uniqueStates = make(map[int64]string, 0)
}
