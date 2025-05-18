Running a controlled execution of a TLA+ model using TLC
---------------------------------

This repository contains:

- `tlc-controlled`: An extended version of the TLC checker that allows to run a controlled schedule of actions of a TLA+ model
- `tla-benchmarks`: A set of TLA+ benchmarks

## Building the TLC checker:

### Follow the following compilation steps in README.md:


Compiling application classes:

``` shell
ant -f customBuild.xml compile
```

(Optional) Compiling test classes:

``` shell
ant -f customBuild.xml compile-test
```


### Build the tlatool.jar file:


``` shell
ant -f customBuild.xml dist
```

### Run TLC:

Running TLC to model check a TLA+ file:

``` shell
 java -jar dist/tla2tools.jar  /path-to-TLA-file/MC.tla -config /path-to-config-file/MC.cfg  
```

Running TLC to simulate a given number of executions of a TLA+ file and log the trace in the given file:

``` shell
 java -jar dist/tla2tools.jar -simulate num=1,file=out.txt /path-to-TLA-file/MC.tla -config /path-to-config-file/MC.cfg  
```

For more options, check `TLC.java`.


### Run TLC in remote controlled mode:

You can run a TLC execution of a given model by controlling the next actions through a remote server.

The `controlled` option runs a controlled TLC execution. The server starts listening on port 2023 for taking the next action and sending the next state of the TLC execution. 

The server and the remote process communicate actions in the json format of the `AbstractAction`. The remote process maps its concrete action (e.g., corresponding to a message delivery) to an abstract protocol action (as in `/src/tlc2/controlled/protocol/AbstractAction`) and sends it to the TLC server. Upon receipt of the `AbstractAction`, the server maps it into the corresponding TLA `Action` and passes it to the TLC checker for its execution. The next state information is sent in the string form from the TLC server to the remore process.

You can check `/src/tlc2/controlled` for more details.

 ``` shell
 java -jar dist/tla2tools.jar -controlled num=1,file=out.txt /path-to-TLA-file/MC.tla -config /path-to-config-file/MC.cfg
 ```
 
#### Sample TLA model files:

You can find sample TLA model files in the folder `TLA-samples` together with some sample traces. The actions in the trace files can be communicated to the TLC server to receive the state information corresponding to the executed actions.

