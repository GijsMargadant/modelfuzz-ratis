package main

import (
	"bufio"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path"
	"path/filepath"
	"strings"
)

type Guider interface {
	Check(iter string, trace *Trace, eventTrace *EventTrace, record bool) (bool, int)
	Coverage() int
	// BranchCoverage() int
	Reset()
}

func NewGuider(fuzzerType FuzzerType, addr, recordPath string, config ClusterConfig) Guider {
	if fuzzerType == ModelFuzz || fuzzerType == RandomFuzzer {
		return NewTLCStateGuider(addr, recordPath)
	} else if fuzzerType == TraceFuzzer {
		return NewTraceCoverageGuider(addr, recordPath)
	} else if fuzzerType == CodeCoverageFuzzer {
		workingDir := "./code_coverage"
		os.RemoveAll(workingDir)
		if config.ServerType == Ratis {
			return NewCodeCoverageGuider(workingDir, config.jacocoLib, config.RatisServerPath)
		} else {
			fmt.Println("Code coverage Guider has not been tested on anything other then Ratis")
			return NewCodeCoverageGuider(workingDir, config.jacocoLib, config.XraftServerPath)
		}
	} else {
		return nil
	}
}

type TLCStateGuider struct {
	TLCAddr   string
	statesMap map[int64]bool
	tlcClient *TLCClient
	// objectPath      string
	// gCovProgramPath string

	recordPath string

	paths Paths
}

var _ Guider = &TLCStateGuider{}

func NewTLCStateGuider(tlcAddr, recordPath string) *TLCStateGuider {
	return &TLCStateGuider{
		TLCAddr:    tlcAddr,
		statesMap:  make(map[int64]bool),
		tlcClient:  NewTLCClient(tlcAddr),
		recordPath: recordPath,
		// objectPath:      objectPath,
		// gCovProgramPath: gCovPath,
		paths: make(Paths, 0),
	}
}

func (t *TLCStateGuider) Reset() {
	t.statesMap = make(map[int64]bool)
	// clearCovData(t.objectPath)
}

func (t *TLCStateGuider) Coverage() int {
	return len(t.statesMap)
}

func (t *TLCStateGuider) Check(iter string, trace *Trace, eventTrace *EventTrace, record bool) (bool, int) {

	numNewStates := 0
	if tlcStates, err := t.tlcClient.SendTrace(eventTrace); err == nil {

		tlcStates = parseTLCStateTrace(tlcStates)
		if len(tlcStates) > 0 {
			path := make(Path, len(tlcStates))
			for i, s := range tlcStates {
				path[i] = PathStep{Key: s.Key, Repr: s.Repr}
			}
			t.paths = append(t.paths, path)
		}

		if record {
			t.recordTrace(iter, trace, eventTrace, tlcStates)
		}
		for _, s := range tlcStates {
			_, ok := t.statesMap[s.Key]
			if !ok {
				numNewStates += 1
				t.statesMap[s.Key] = true
			}
		}
	}
	return numNewStates != 0, numNewStates
}

func (t *TLCStateGuider) Paths() Paths {
	return t.paths
}

func (t *TLCStateGuider) DumpPaths(filePath string) error {
	data, err := json.MarshalIndent(t.paths, "", "\t")
	if err != nil {
		return err
	}
	return os.WriteFile(filePath, data, 0o644)
}

func (t *TLCStateGuider) recordTrace(as string, trace *Trace, eventTrace *EventTrace, states []TLCState) {

	filePath := path.Join(t.recordPath, as+".json")
	data := map[string]interface{}{
		"trace":       trace,
		"event_trace": eventTrace,
		"state_trace": parseTLCStateTrace(states),
	}
	dataB, err := json.MarshalIndent(data, "", "\t")
	if err != nil {
		return
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

func parseTLCStateTrace(states []TLCState) []TLCState {
	newStates := make([]TLCState, len(states))
	for i, s := range states {
		repr := strings.ReplaceAll(s.Repr, "\n", ",")
		repr = strings.ReplaceAll(repr, "/\\", "")
		repr = strings.ReplaceAll(repr, "\u003e\u003e", "]")
		repr = strings.ReplaceAll(repr, "\u003c\u003c", "[")
		repr = strings.ReplaceAll(repr, "\u003e", ">")
		newStates[i] = TLCState{
			Repr: repr,
			Key:  s.Key,
		}
	}
	return newStates
}

type TraceCoverageGuider struct {
	traces map[string]bool
	*TLCStateGuider
}

var _ Guider = &TraceCoverageGuider{}

func NewTraceCoverageGuider(tlcAddr, recordPath string) *TraceCoverageGuider {
	return &TraceCoverageGuider{
		traces:         make(map[string]bool),
		TLCStateGuider: NewTLCStateGuider(tlcAddr, recordPath),
	}
}

func (t *TraceCoverageGuider) Check(iter string, trace *Trace, events *EventTrace, record bool) (bool, int) {
	t.TLCStateGuider.Check(iter, trace, events, record)

	eTrace := newEventTrace(events)
	key := eTrace.Hash()

	new := 0
	if _, ok := t.traces[key]; !ok {
		t.traces[key] = true
		new = 1
	}

	return new != 0, new
}

func (t *TraceCoverageGuider) Coverage() int {
	return t.TLCStateGuider.Coverage()
}

func (t *TraceCoverageGuider) Reset() {
	t.traces = make(map[string]bool)
	t.TLCStateGuider.Reset()
}

type eventTrace struct {
	Nodes map[string]*eventNode
}

func (e *eventTrace) Hash() string {
	bs, err := json.Marshal(e)
	if err != nil {
		return ""
	}
	hash := sha256.Sum256(bs)
	return hex.EncodeToString(hash[:])
}

type eventNode struct {
	Event
	Node string
	Prev string
	ID   string `json:"-"`
}

func (e *eventNode) Hash() string {
	bs, err := json.Marshal(e)
	if err != nil {
		return ""
	}
	hash := sha256.Sum256(bs)
	return hex.EncodeToString(hash[:])
}

func newEventTrace(events *EventTrace) *eventTrace {
	eTrace := &eventTrace{
		Nodes: make(map[string]*eventNode),
	}
	curEvent := make(map[string]*eventNode)

	for _, e := range events.Events {
		node := &eventNode{
			Event: e.Copy(),
			Node:  e.Node,
			Prev:  "",
		}
		prev, ok := curEvent[e.Node]
		if ok {
			node.Prev = prev.ID
		}
		node.ID = node.Hash()
		curEvent[e.Node] = node
		eTrace.Nodes[node.ID] = node
	}
	return eTrace
}

type CodeCoverageGuider struct {
	outputDir string
	jacocoCli string
	jar       string
}

func NewCodeCoverageGuider(recordPath string, jacocoLib string, jar string) *CodeCoverageGuider {
	return &CodeCoverageGuider{
		outputDir: recordPath,
		jacocoCli: filepath.Join(jacocoLib, "jacococli.jar"),
		jar:       jar,
	}
}

// Iteration coverage
func (c *CodeCoverageGuider) Check(iter string, trace *Trace, eventTrace *EventTrace, record bool) (bool, int) {
	baseDir := c.outputDir
	clusterDir := filepath.Join(baseDir, "cluster")

	// Get coverage files from all nodes and from the previous iteration
	iterationCoverageFiles, err := GetJacocoFiles(clusterDir)
	if err != nil {
		panic("Unable to obtain jacoco coverage files: " + string(err.Error()))
	}
	if len(iterationCoverageFiles) == 0 {
		panic("Unable to obtain any jacoco coverage files")
	}
	previousCoverageFile := filepath.Join(baseDir, "jacoco.exec")
	coverageFiles := iterationCoverageFiles
	if _, err := os.Stat(previousCoverageFile); err == nil {
		coverageFiles = append(iterationCoverageFiles, previousCoverageFile)
	}

	for _, f := range coverageFiles {
		generateJacocoReport(c.jacocoCli, c.jar, f, filepath.Join(filepath.Dir(f), "report.html"))
	}

	// Merge all available coverage data
	newCoverageFile := filepath.Join(clusterDir, "jacoco.exec")
	err = MergeCoverageFiles(c.jacocoCli, coverageFiles, newCoverageFile)
	if err != nil {
		panic("Error while merging coverage files: " + string(err.Error()))
	}

	// Generate coverage report
	newCoverageReport := filepath.Join(clusterDir, "report.xml")
	err = generateJacocoReport(c.jacocoCli, c.jar, newCoverageFile, newCoverageReport)
	if err != nil {
		panic("Error while obtaining coverage report: " + string(err.Error()))
	}

	previousCoverageReport := filepath.Join(baseDir, "report.xml")
	coverageIncrease := 0
	if _, err := os.Stat(previousCoverageReport); os.IsNotExist(err) {
		// First iteration, no previous coverage report exists
		coverage, err := lineCoverage(newCoverageReport)
		if err != nil {
			panic("Error while extracting first iteration code coverage: " + string(err.Error()))
		}
		coverageIncrease = coverage
	} else {
		coverage, err := ComputeCoverageIncrease(newCoverageReport, previousCoverageReport)
		if err != nil {
			panic("Error while computing code coverage increase: " + string(err.Error()))
		}
		coverageIncrease = coverage
	}

	err = copyFile(newCoverageFile, previousCoverageFile)
	if err != nil {
		panic("Failed to update global coverage file: " + err.Error())
	}
	err = copyFile(newCoverageReport, previousCoverageReport)
	if err != nil {
		panic("Failed to update global report file: " + err.Error())
	}
	// os.RemoveAll(clusterDir)

	htmlReport := filepath.Join(baseDir, "report.html")
	coverageFile := filepath.Join(baseDir, "jacoco.exec")
	err = generateJacocoReport(c.jacocoCli, c.jar, coverageFile, htmlReport)
	if err != nil {
		panic("Error while obtaining coverage report: " + string(err.Error()))
	}

	return coverageIncrease != 0, coverageIncrease
}

func copyFile(src, dst string) error {
	input, err := os.ReadFile(src)
	if err != nil {
		return err
	}
	return os.WriteFile(dst, input, 0644)
}

// Current total coverage
func (c *CodeCoverageGuider) Coverage() int {
	report := filepath.Join(c.outputDir, "report.xml")
	if _, err := os.Stat(report); os.IsNotExist(err) {
		panic("No coverage report found")
	}

	coverage, err := lineCoverage(report)
	if err != nil {
		panic("Unable to extract line coverage from report")
	}

	return coverage
}

func (c *CodeCoverageGuider) Reset() {
	os.Remove(filepath.Join(c.outputDir, "report.xml"))
	os.Remove(filepath.Join(c.outputDir, "jacoco.exec"))
}

var _ Guider = &CodeCoverageGuider{}
