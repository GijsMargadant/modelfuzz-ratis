package main

import (
	"fmt"
	"os"
	"strconv"
	"sync"
)

func main() {
    // Define k values to run sequentially
    kValues := []int{2, 3}
    
    for _, k := range kValues {
        
        fmt.Printf("Starting experiment with k= %d\n", k)
        
        // Run experiment with current k value
        runExperiment(k, 123456789)
        
        fmt.Printf("Completed experiment with k=%d\n", k)
    }
    
    fmt.Println("All experiments completed")
}

func runExperiment(k int, seed int) {
	logLevel := "DEBUG"
	numNodes := 5

	//argsWithoutProg := os.Args[1:]
	// seed, _ := strconv.Atoi(argsWithoutProg[0])
	// fmt.Println("Random seed: " + argsWithoutProg[0])
	fmt.Println("Random seed: " + strconv.Itoa(seed))
	fuzzerType := KPathFuzzer
	//k := 1   // set to two ks to test both k=2 and k=3

	var wg sync.WaitGroup
	// for i := 0; i <= 2; i++ {
	config := FuzzerConfig{
		// TimeBudget:			60,
		Horizon:           200,
		Iterations:        1000,
		NumNodes:          numNodes,
		LogLevel:          logLevel,
		NetworkPort:       7074 + k, // + i,
		RatisDataDir:      "./data",
		BaseWorkingDir:    "./output/" + fuzzerType.String() + strconv.Itoa(k), // FuzzerType(i).String(),
		MutationsPerTrace: 3,
		SeedPopulation:    20, 
		NumRequests:       0,
		NumCrashes:        5,
		MaxMessages:       5,
		ReseedFrequency:   200,
		RandomSeed:        seed,
		SubPathLength:     k,

		ClusterConfig: &ClusterConfig{
			FuzzerType:          fuzzerType, // FuzzerType(i),
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

	fuzzer, err := NewFuzzer(config, fuzzerType)
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
