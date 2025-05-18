package main

import (
	"fmt"
	"math/rand"
)

type Mutator interface {
	Mutate(*Trace, *EventTrace) (*Trace, bool)
}

type randomMutator struct{}

func (r *randomMutator) Mutate(_ *Trace, _ *EventTrace) (*Trace, bool) {
	return nil, false
}

func RandomMutator() Mutator {
	return &randomMutator{}
}

type SwapCrashNodeMutator struct {
	NumSwaps int
	r        *rand.Rand
}

var _ Mutator = &SwapCrashNodeMutator{}

func NewSwapCrashNodeMutator(swaps int, random *rand.Rand) *SwapCrashNodeMutator {
	return &SwapCrashNodeMutator{
		NumSwaps: swaps,
		r:        random, // rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

func (s *SwapCrashNodeMutator) Mutate(trace *Trace, eventTrace *EventTrace) (*Trace, bool) {
	swaps := make(map[int]int)

	nodeChoices := make([]int, 0)
	for i, ch := range trace.Choices {
		if ch.Type == "Crash" {
			nodeChoices = append(nodeChoices, i)
		}
	}

	if len(nodeChoices) < s.NumSwaps*2 {
		return nil, false
	}

	for len(swaps) < s.NumSwaps {
		sp := sample(nodeChoices, 2, s.r)
		swaps[sp[0]] = sp[1]
	}

	newTrace := trace.Copy()
	for i, j := range swaps {
		iCh := newTrace.Choices[i]
		jCh := newTrace.Choices[j]

		iChNew := iCh.Copy()
		iChNew.Node = jCh.Node
		jChNew := jCh.Copy()
		jChNew.Node = iCh.Node

		newTrace.Choices[i] = iChNew
		newTrace.Choices[j] = jChNew
	}
	return newTrace, true
}

type SwapNodeMutator struct {
	NumSwaps int
	rand     *rand.Rand
}

var _ Mutator = &SwapNodeMutator{}

func NewSwapNodeMutator(swaps int, random *rand.Rand) *SwapNodeMutator {
	return &SwapNodeMutator{
		NumSwaps: swaps,
		rand:     random, // rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

func (s *SwapNodeMutator) Mutate(trace *Trace, _ *EventTrace) (*Trace, bool) {
	nodeChoiceIndices := make([]int, 0)
	for i, choice := range trace.Choices {
		if choice.Type == "Node" {
			nodeChoiceIndices = append(nodeChoiceIndices, i)
		}
	}
	numNodeChoiceIndices := len(nodeChoiceIndices)
	if numNodeChoiceIndices == 0 {
		return nil, false
	}
	choices := numNodeChoiceIndices
	if s.NumSwaps < choices {
		choices = s.NumSwaps
	}
	toSwap := make(map[string]map[int]int)
	for len(toSwap) < choices {
		i := nodeChoiceIndices[s.rand.Intn(numNodeChoiceIndices)]
		j := nodeChoiceIndices[s.rand.Intn(numNodeChoiceIndices)]
		key := fmt.Sprintf("%d_%d", i, j)
		if _, ok := toSwap[key]; !ok {
			toSwap[key] = map[int]int{i: j}
		}
	}
	newTrace := trace.Copy()
	for _, v := range toSwap {
		for i, j := range v {
			first := newTrace.Choices[i]
			second := newTrace.Choices[j]
			newTrace.Choices[i] = second.Copy()
			newTrace.Choices[j] = first.Copy()
		}
	}
	return newTrace, true
}

type SwapMaxMessagesMutator struct {
	NumSwaps int
	r        *rand.Rand
}

var _ Mutator = &SwapMaxMessagesMutator{}

func NewSwapMaxMessagesMutator(swaps int, random *rand.Rand) *SwapMaxMessagesMutator {
	return &SwapMaxMessagesMutator{
		NumSwaps: swaps,
		r:        random, // rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

func (s *SwapMaxMessagesMutator) Mutate(trace *Trace, eventTrace *EventTrace) (*Trace, bool) {
	swaps := make(map[int]int)

	nodeChoices := make([]int, 0)
	for i, ch := range trace.Choices {
		if ch.Type == "Node" {
			nodeChoices = append(nodeChoices, i)
		}
	}

	if len(nodeChoices) < s.NumSwaps {
		return nil, false
	}

	for len(swaps) < s.NumSwaps {
		sp := sample(nodeChoices, 2, s.r)
		swaps[sp[0]] = sp[1]
	}

	newTrace := trace.Copy()
	for i, j := range swaps {
		iCh := newTrace.Choices[i]
		jCh := newTrace.Choices[j]

		iChNew := iCh.Copy()
		iChNew.MaxMessages = jCh.MaxMessages
		jChNew := jCh.Copy()
		jChNew.MaxMessages = iCh.MaxMessages

		newTrace.Choices[i] = iChNew
		newTrace.Choices[j] = jChNew
	}
	return newTrace, true
}

type combinedMutator struct {
	mutators []Mutator
}

var _ Mutator = &combinedMutator{}

func (c *combinedMutator) Mutate(trace *Trace, eventTrace *EventTrace) (*Trace, bool) {
	curTrace := trace.Copy()
	for _, m := range c.mutators {
		nextTrace, ok := m.Mutate(curTrace, eventTrace)
		if !ok {
			return nil, false
		}
		curTrace = nextTrace
	}
	return curTrace, true
}

func CombineMutators(mutators ...Mutator) Mutator {
	return &combinedMutator{
		mutators: mutators,
	}
}