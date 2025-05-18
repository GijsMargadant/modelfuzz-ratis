------------------------------- MODULE MicroBenchmark -------------------------------
EXTENDS Naturals, TLC, FiniteSets

CONSTANT N   \* The number of requests
CONSTANT M   \* The set of workers

VARIABLES
    appMasterState,
    registeredWorkers,
    registeredTerminator,
    workerState,
    workerBuffer,
    terminatorState,
    curExecute,
    msgs
    
Messages ==
    [type : {"RegisterTerminator"}] \cup [type : {"Execute"}, req : (0..N), worker : M] \cup [type : {"RegisterWorker", "Terminate", "Flush"}, worker : M]
    
TypeOK ==
    /\ appMasterState \in {"init", "inProgress"}
    /\ registeredWorkers \subseteq M
    /\ registeredTerminator \in {0,1}
    /\ workerState \in [M -> [state: {"working", "completed", "flushed"}]]
    /\ workerBuffer \subseteq (0..N) 
    /\ terminatorState \in {"working", "terminating"}
    /\ curExecute \in (0..N)
    /\ msgs \subseteq Messages
    
Init ==
    /\ appMasterState = "init"
    /\ registeredWorkers = {}
    /\ registeredTerminator = 0
    /\ workerState = [w \in M |-> [state |-> "working"]]
    /\ workerBuffer = {}
    /\ terminatorState = "working"
    /\ curExecute = 0
    /\ msgs = {}

WorkerRegister(w) ==
    /\ workerState[w]["state"] = "working"
    /\ msgs' = msgs \cup {[type |-> "RegisterWorker", worker |-> w]}
    /\ UNCHANGED <<appMasterState, registeredWorkers, registeredTerminator, workerState, workerBuffer, terminatorState, curExecute>>

MasterRegisterWorker(w) ==
    /\ appMasterState = "init"
    /\ [type |-> "RegisterWorker", worker |-> w] \in msgs
    /\ registeredWorkers' = registeredWorkers \cup {w}
    /\ UNCHANGED <<appMasterState, registeredTerminator, workerState, workerBuffer, terminatorState, curExecute, msgs>>

TerminatorRegister ==
    /\ terminatorState = "working"
    /\ msgs' = msgs \cup {[type |-> "RegisterTerminator"]}
    /\ UNCHANGED <<appMasterState, registeredWorkers, registeredTerminator, workerState, workerBuffer, terminatorState, curExecute>>

MasterRegisterTerminator ==
    /\ appMasterState = "init"
    /\ [type |-> "RegisterTerminator"] \in msgs
    /\ registeredTerminator' = 1
    /\ UNCHANGED <<appMasterState, registeredWorkers, workerState, workerBuffer, terminatorState, curExecute, msgs>>
    
MasterRcvRequest(w, r) ==
    /\ r = curExecute
    /\ appMasterState = "init"
    /\ registeredWorkers = M
    /\ registeredTerminator = 1
    /\ msgs' = msgs \cup {[type |-> "Execute", req |-> r, worker |-> 1], [type |-> "Terminate", worker |-> 1]}
    /\ appMasterState' = "inProgress"
    /\ UNCHANGED <<registeredWorkers, registeredTerminator, workerState, workerBuffer, terminatorState, curExecute>>
    
WorkerRcvExecute(w, r) ==
    /\ r = curExecute
    /\ workerState[w] = [state |-> "working"]
    /\ [type |-> "Execute", req |-> r, worker |-> w] \in msgs
    /\ workerBuffer' = workerBuffer \cup {r} 
    /\ msgs' = msgs \cup {[type |-> "Execute", req |-> r + 1, worker |-> w]}
    /\ workerState' = IF r = N THEN [workerState EXCEPT ![w] = [state |-> "completed"]] ELSE workerState
    /\ curExecute' = curExecute + 1
    /\ UNCHANGED <<appMasterState, registeredWorkers, registeredTerminator, terminatorState>>
    
TerminatorRcvTerminate(w) ==
    /\ terminatorState = "working"
    /\ [type |-> "Terminate", worker |-> w] \in msgs
    /\ msgs' = msgs \cup {[type |-> "Flush", worker |-> w]}
    /\ terminatorState' = "terminating"
    /\ UNCHANGED <<appMasterState, registeredWorkers, registeredTerminator, workerState, workerBuffer, curExecute>>

WorkerRcvFlush(w) ==
    /\ (workerState[w]["state"] = "working") \/ (workerState[w]["state"] = "completed")
    /\ [type |-> "Flush", worker |-> w] \in msgs
    /\ workerBuffer' = {}
    /\ workerState' = [workerState EXCEPT ![w] = [state |-> "flushed"]]
    /\ UNCHANGED <<appMasterState, registeredWorkers, registeredTerminator, terminatorState, curExecute, msgs>>

Next ==
    \/ TerminatorRegister
    \/ MasterRegisterTerminator
    \/ \E r \in (0..N), w \in M:
        WorkerRcvExecute(w, r) \/ WorkerRegister(w) \/ TerminatorRcvTerminate(w) \/ WorkerRcvFlush(w) \/ MasterRegisterWorker(w) \/ MasterRcvRequest(w, r)

Spec == Init /\ [][Next]_<<appMasterState, registeredWorkers, registeredTerminator, workerState, terminatorState, workerBuffer, curExecute, msgs>>

THEOREM Spec => [](TypeOK)
    
    


=============================================================================
