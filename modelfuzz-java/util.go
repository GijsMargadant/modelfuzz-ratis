package main

import (
	"math/rand"
)

func sample(l []int, size int, r *rand.Rand) []int {
	if size >= len(l) {
		return l
	}
	indexes := make(map[int]bool)
	for len(indexes) < size {
		i := r.Intn(len(l))
		indexes[i] = true
	}
	samples := make([]int, size)
	i := 0
	for k := range indexes {
		samples[i] = l[k]
		i++
	}
	return samples
}

func intRange(start, end int) []int {
	res := make([]int, end-start)
	for i := start; i < end; i++ {
		res[i-start] = i
	}
	return res
}