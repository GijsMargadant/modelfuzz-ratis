package main

import (
	"bufio"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path"
	// "path/filepath"
	"strings"
)

type Guider interface {
	Check(iter string, trace *Trace, eventTrace *EventTrace, record bool) (bool, int)
	Coverage() int
	Reset()

	Paths() Paths
	DumpPaths(filePath string) error
	DumpSubPaths(filePath string) error
}

func NewGuider(fuzzerType FuzzerType, addr, recordPath string, k int) Guider {
	if fuzzerType == ModelFuzz || fuzzerType == RandomFuzzer {
		return NewTLCStateGuider(addr, recordPath, k)
	} else if fuzzerType == TraceFuzzer {
		return NewTraceCoverageGuider(addr, recordPath, k)
	} else {
		return nil
	}
}

type TLCStateGuider struct {
	TLCAddr   string
	statesMap map[int64]bool
	tlcClient *TLCClient
	logger    *Logger

	singleExecutionPath string
	overallPaths        Paths
	K                   int
	subPathsMap         map[string]bool
}

var _ Guider = &TLCStateGuider{}

func NewTLCStateGuider(tlcAddr, recordPath string, k int) *TLCStateGuider {
	return &TLCStateGuider{
		TLCAddr:             tlcAddr,
		statesMap:           make(map[int64]bool),
		tlcClient:           NewTLCClient(tlcAddr),
		singleExecutionPath: recordPath,
		logger:              NewLogger(),

		overallPaths: make(Paths, 0),
		subPathsMap:  make(map[string]bool),
		K:            k,
	}
}

func (t *TLCStateGuider) Reset() {
	t.statesMap = make(map[int64]bool)
}

func (t *TLCStateGuider) Coverage() int {
	return len(t.statesMap)
}

func (t *TLCStateGuider) Check(iter string, trace *Trace, eventTrace *EventTrace, record bool) (bool, int) {

	numNewStates := 0
	if tlcStates, err := t.tlcClient.SendTrace(eventTrace); err == nil {

		// Add to Check method
		t.logger.Info(fmt.Sprintf("\nK=%d, tlcStates length=%d\n", t.K, len(tlcStates)))

		tlcStates = parseTLCStateTrace(tlcStates)
		if len(tlcStates) > 0 {
			path := make(Path, len(tlcStates))
			for i, s := range tlcStates {
				path[i] = PathStep{Key: s.Key, Repr: s.Repr}
			}
			t.overallPaths = append(t.overallPaths, path)
		}

		if record {
			t.recordTrace(iter, trace, eventTrace, tlcStates)
		}

		for _, s := range tlcStates {
			if _, seen := t.statesMap[s.Key]; !seen {
				numNewStates += 1
				t.statesMap[s.Key] = true
			}
		}

		// ======================================================
		//     Extract all contiguous subpaths of length K = t.K
		//     from this newly‐observed path, and count how many are new.
		//     Serialize each subpath as "key_i,key_{i+1},...,key_{i+K-1}".
		// ======================================================
		numNewSubpaths := 0
		K := t.K
		newSubpathKeys := make([]string, 0, numNewSubpaths)
		if len(tlcStates) >= K {
			// slide a window of size K over 'tlcStates'
			for i := 0; i <= len(tlcStates)-K; i++ {
				// build key from tlcStates[i].Key … tlcStates[i+K-1].Key
				keyParts := make([]string, K)
				for j := 0; j < K; j++ {
					keyParts[j] = fmt.Sprintf("%d", tlcStates[i+j].Key)
					t.logger.Info(fmt.Sprintf("Subpath key part: %s", keyParts[j]))
				}
				subpathKey := strings.Join(keyParts, ",")

				if _, seen := t.subPathsMap[subpathKey]; !seen {
					t.logger.Info(fmt.Sprintf("New subpath found: %s", subpathKey))
					numNewSubpaths += 1
					t.subPathsMap[subpathKey] = true
					newSubpathKeys = append(newSubpathKeys, subpathKey)
				}
			}
		}

		if numNewSubpaths > 0 {
			// write a log line for this iteration
			// t.logger.Debug(fmt.Sprintf("ITER=%s NEW_SUBPATHS=%d KEYS=%q\n", iter, numNewSubpaths, newSubpathKeys))
			t.logger.Debug(fmt.Sprintf("NEW_SUBPATHS=%d", numNewSubpaths))
			t.logger.Info(fmt.Sprintf("NEW_SUBPATHS=%d KEYS=%q", numNewSubpaths, newSubpathKeys))
		}

		return numNewSubpaths != 0, numNewSubpaths
	}
	return false, 0
}

func (t *TLCStateGuider) Paths() Paths {
	return t.overallPaths
}

func (t *TLCStateGuider) DumpPaths(filePath string) error {
	data, err := json.MarshalIndent(t.overallPaths, "", "\t")
	if err != nil {
		return err
	}
	return os.WriteFile(filePath, data, 0o644)
}

func (t *TLCStateGuider) DumpSubPaths(filePath string) error {
	// Convert map keys to a slice for JSON serialization
	if len(t.subPathsMap) == 0 {
		data, err := json.MarshalIndent("Subpaths empty - somenthings wrong", "", "\t")
		if err != nil {
			return err
		}
		return os.WriteFile(filePath, data, 0o644)
	}
	subpaths := make([]string, 0, len(t.subPathsMap))
	for subpath := range t.subPathsMap {
		subpaths = append(subpaths, subpath)
	}

	data, err := json.MarshalIndent(subpaths, "", "\t")
	if err != nil {
		return err
	}
	return os.WriteFile(filePath, data, 0o644)
}

func (t *TLCStateGuider) recordTrace(as string, trace *Trace, eventTrace *EventTrace, states []TLCState) {

	filePath := path.Join(t.singleExecutionPath, as+".json")
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

func NewTraceCoverageGuider(tlcAddr, recordPath string, k int) *TraceCoverageGuider {
	return &TraceCoverageGuider{
		traces:         make(map[string]bool),
		TLCStateGuider: NewTLCStateGuider(tlcAddr, recordPath, k),
	}
}

func (t *TraceCoverageGuider) DumpSubPaths(filePath string) error {
	return t.TLCStateGuider.DumpSubPaths(filePath)
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
