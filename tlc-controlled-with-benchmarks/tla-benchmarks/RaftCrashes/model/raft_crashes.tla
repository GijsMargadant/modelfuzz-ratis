--------------------------------- MODULE raft_crashes ---------------------------------

\* A specfication of the raft protocol which models only the active state of the protocols for each timestep

EXTENDS Naturals, FiniteSets, Sequences, TLC

\* The set of server IDs
CONSTANTS Server

\* Server states.
CONSTANTS Follower, Candidate, Leader

\* Maximum term number that can be observed in an execution
CONSTANTS MaxTerm

\* Maximum index of a log entry
CONSTANTS MaxIndex

----

\* Set of currently active servers
VARIABLE currentActive

VARIABLE state

VARIABLE leaderTerm

VARIABLE lastLog

VARIABLE commitIndex

VARIABLE snapshotIndex

vars == <<currentActive, state, leaderTerm, lastLog, commitIndex, snapshotIndex>>

----

AllStates == {Follower, Candidate, Leader}

AllTerms == 0..MaxTerm

AllIndices == 0..MaxIndex

----

Init == /\ currentActive = Server
        /\ state = [i \in Server |-> Follower]
        /\ leaderTerm = 0
        /\ lastLog = [i \in Server |-> 0]
        /\ commitIndex = [i \in Server |-> 0]
        /\ snapshotIndex = [i \in Server |-> 0]

Remove(i) == 
    /\ state' = [j \in {x \in currentActive: x # i} |-> state[j]]
    /\ currentActive' = currentActive \ {i}
    /\ UNCHANGED <<leaderTerm, lastLog, commitIndex, snapshotIndex>>

UpdateState(i, s, li, ci) ==
    /\ i \in currentActive
    /\ ci <= li
    /\ state' = [state EXCEPT ![i] = s]
    /\ lastLog' = [lastLog EXCEPT ![i] = li]
    /\ commitIndex' = [commitIndex EXCEPT ![i] = ci]
    /\ UNCHANGED <<currentActive, leaderTerm, snapshotIndex>>

UpdateLeaderTerm(i, t) == 
    /\ i \in currentActive
    /\ state' = [state EXCEPT ![i] = Leader]
    /\ leaderTerm' = t
    /\ UNCHANGED <<currentActive, lastLog, commitIndex, snapshotIndex>>

UpdateSnapshot(i, si) ==
    /\ i \in currentActive
    /\ lastLog[i] > 0
    /\ si <= lastLog[i].index
    /\ snapshotIndex' = [snapshotIndex EXCEPT ![i] = si]
    /\ UNCHANGED <<currentActive, state, lastLog, leaderTerm, commitIndex, snapshotIndex>>

Add(i) ==
    LET newActive == currentActive \union {i}
    IN 
        /\ currentActive' = newActive
        /\ state' = [j \in newActive |-> IF j \in currentActive THEN state[j] ELSE Follower]
        /\ UNCHANGED <<leaderTerm, lastLog, commitIndex, snapshotIndex>>

Next == \/ \E i \in Server : Remove(i)
        \/ \E i \in Server, s \in AllStates, li,ci \in AllIndices : UpdateState(i, s, li, ci)
        \/ \E i \in Server, si \in AllIndices : UpdateSnapshot(i, si)
        \/ \E i \in Server : Add(i)
        \/ \E i \in Server, t \in AllTerms: UpdateLeaderTerm(i, t)


Spec == Init /\ [][Next]_vars


===================================================================================