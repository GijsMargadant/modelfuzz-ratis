# ModelFuzz (CS4720)

## Prerequisites
- Install go
- Install java
- Install ant
- Install maven

## Build

### modelfuzz-java
Set current working directory to `modelfuzz-java`.
``` shell
go build
```

### tlc-server
Set current working directory to `tlc-controlled-with-benchmarks/tlc-controlled`.
``` shell
ant -f customBuild.xml compile
ant -f customBuild.xml compile-test
ant -f customBuild.xml dist
```

### ratis-fuzzing
Set current working directory to `ratis-fuzzing`.
``` shell
mvn clean package -DskipTests
```

## Run
First, run the TLC server. Run the following command from `tlc-controlled-with-benchmarks/tlc-controlled`:
``` shell
    java -jar dist/tla2tools_server.jar -controlled <path-to-tla-file> -config <path-to-cfg-file> -mapperparams "name=raft"
```
where `<path-to-a-tla-file>` can be one of the files inside `tla-benchmarks/Raft/model`. For example:
``` shell
java -jar dist/tla2tools_server.jar -controlled ..\tla-benchmarks\Raft\model\RAFT_1_3.tla -config ..\tla-benchmarks\Raft\model\RAFT_1_3.cfg -mapperparams "name=raft"
```

Now, within the `modelfuzz` directory, run the fuzzer using:
``` shell
./modelfuzz-java
```
The seed is not included in the comand, instead it can be specified in main.go