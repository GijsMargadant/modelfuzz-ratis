package main

import (
	"fmt"
	"os"
	"strconv"
	"sync"
)

func main() {
	logLevel := "DEBUG"
	numNodes := 3

	argsWithoutProg := os.Args[1:]
	seed, _ := strconv.Atoi(argsWithoutProg[0])
	fmt.Println("Random seed: " + argsWithoutProg[0])

	var wg sync.WaitGroup
	// for i := 0; i <= 2; i++ {
	config := FuzzerConfig{
		// TimeBudget:			60,
		Horizon:           200,
		Iterations:        1,
		NumNodes:          numNodes,
		LogLevel:          logLevel,
		NetworkPort:       7074,                             // + i,
		RatisDataDir:      "./data",
		BaseWorkingDir:    "./output/" + ModelFuzz.String(), // FuzzerType(i).String(),
		MutationsPerTrace: 3,
		SeedPopulation:    20,
		NumRequests:       3,
		NumCrashes:        0,
		MaxMessages:       5,
		ReseedFrequency:   200,
		RandomSeed:        seed,

		ClusterConfig: &ClusterConfig{
			FuzzerType:          ModelFuzz, // FuzzerType(i),
			NumNodes:            numNodes,
			ServerType:          Ratis,
			XraftServerPath:     "../xraft-controlled/xraft-kvstore/target/xraft-kvstore-0.1.0-SNAPSHOT-bin/xraft-kvstore-0.1.0-SNAPSHOT/bin/xraft-kvstore",
			XraftClientPath:     "../xraft-controlled/xraft-kvstore/target/xraft-kvstore-0.1.0-SNAPSHOT-bin/xraft-kvstore-0.1.0-SNAPSHOT/bin/xraft-kvstore-cli",
			RatisServerPath:     "../ratis-fuzzing/ratis-examples/target/ratis-examples-2.5.1.jar",
			RatisClientPath:     "../ratis-fuzzing/ratis-examples/target/ratis-examples-2.5.1.jar",
			RatisLog4jConfig:    "-Dlog4j.configuration=file:../ratis-fuzzing/ratis-examples/src/main/resources/log4j.properties",
			BaseGroupPort:       2330 + ((numNodes + 1) * 100), //(i * (numNodes + 1) * 100),
			BaseServicePort:     3330 + ((numNodes + 1) * 100), //(i * (numNodes + 1) * 100),
			BaseInterceptorPort: 7000 + ((numNodes + 1) * 100), //(i * (numNodes + 1) * 100),
			LogLevel:            logLevel,
		},
		TLCPort: 2023,
	}

	if _, err := os.Stat(config.BaseWorkingDir); err == nil {
		os.RemoveAll(config.BaseWorkingDir)
	}
	os.MkdirAll(config.BaseWorkingDir, 0777)

	fuzzer, err := NewFuzzer(config, ModelFuzz)
	if err != nil {
		fmt.Errorf("Could not create fuzzer %e", err)
		return
	}

	wg.Add(1)
	go func() {
		fuzzer.Run()
		wg.Done()
	}()
	// }
	wg.Wait()

}
