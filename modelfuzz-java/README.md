# ModelFuzz-Java

ModelFuzz is a fuzzer developed for fuzzing distributed systems. This repository supports testing Ratis and Xraft. 

In the parent folder of this repository, please clone:

`https://github.com/egeberkaygulcan/xraft-controlled`

`https://github.com/egeberkaygulcan/ratis-fuzzing`

`https://github.com/burcuku/tlc-controlled-with-benchmarks`

## TLC
Within the `tlc-controlled-with-benchmarks` repository folder, first replace the tlc-controlled folder with the clone of:

`https://github.com/burcuku/tlc-controlled`

To compile the tlc controlled repository: 

``` shell
ant -f customBuild.xml compile
```

(Optional) Compiling test classes:

``` shell
ant -f customBuild.xml compile-test
```

Build the tlatool.jar file:

``` shell
ant -f customBuild.xml dist
```

To run TLC, within the tlc-controlled fodler:

``` shell
java -jar dist/tla2tools_server.jar -controlled <path-to-tla-file> -config <path-to-cfg-file> -mapperparams "name=raft"
```

The *.tla* and *.cfg* files can be found within the *tla-benchmarks* folder. Please use the models in the *Raft* folder. Additionally, please change `raft_alt` in the **EXTENDS** part of the *.tla* files in to `raft-enhanced` if it is not already changed.

## Ratis
To build Ratis (within the Ratis repository folder), you can use: 

``` shell
mvn clean package -DskipTests
```

## Xraft
To build Xraft (within the Xraft repository folder):

``` shell
mvn clean compile install
```

``` shell
cd xraft-kvstore
```

``` shell
mvn package assembly:single
```

## ModelFuzz usage

You can use this repository by building and running the executable:

``` shell
go build
```

``` shell
./modelfuzz-java
```

Example configuration is given in the *main.go* file

### Parameters

-TODO

