--------------------------------- MODULE raft_alt ---------------------------------

\* This is a formal specification of the raft consensus algorithm without the bag message semantics. 
\* Some assumptions on the model to ensure the set of actions can be enumerated
\* 1. We bound the largest term with a constant
\* 2. We also bound the largest log index


EXTENDS Naturals, FiniteSets, Sequences, TLC

\* The set of server IDs
CONSTANTS Server

\* The set of requests that can go into the log
CONSTANTS MaxValue

\* Server states.
CONSTANTS Follower, Candidate, Leader

\* A reserved value.
CONSTANTS Nil

\* Maxmimum log index
CONSTANTS MaxLogIndex

\* Largest term 
CONSTANTS LargestTerm

----

\* The following variables are all per server (functions with domain Server).

\* The server's term number.
VARIABLE currentTerm
\* The server's state (Follower, Candidate, or Leader).
VARIABLE state
\* The candidate the server voted for in its current term, or
\* Nil if it hasn't voted for any.
VARIABLE votedFor
serverVars == <<currentTerm, state, votedFor>>

\* A Sequence of log entries. The index into this sequence is the index of the
\* log entry. Unfortunately, the Sequence module defines Head(s) as the entry
\* with index 1, so be careful not to use that!
VARIABLE log
\* The index of the latest entry in the log the state machine may apply.
VARIABLE commitIndex
logVars == <<log, commitIndex>>

\* The following variables are used only on candidates:
\* The set of servers from which the candidate has received a RequestVote
\* response in its currentTerm.
VARIABLE votesResponded
\* The set of servers from which the candidate has received a vote in its
\* currentTerm.
VARIABLE votesGranted
candidateVars == <<votesResponded, votesGranted>>

\* The following variables are used only on leaders:
\* The next entry to send to each follower.
VARIABLE nextIndex
\* The latest entry that each follower has acknowledged is the same as the
\* leader's. This is used to calculate commitIndex on the leader.
VARIABLE matchIndex
leaderVars == <<nextIndex, matchIndex>>

\* End of per server variables.

----

\* All variables; used for stuttering (asserting state hasn't changed).
\* vars == <<messages, allLogs, serverVars, candidateVars, leaderVars, logVars>>
vars == <<serverVars, candidateVars, leaderVars, logVars>>

----

\* Helpers

\* The set of all quorums. This just calculates simple majorities, but the only
\* important property is that every quorum overlaps with every other.
Quorum == {i \in SUBSET(Server) : Cardinality(i) * 2 > Cardinality(Server)}

\* The term of the last entry in a log, or 0 if the log is empty.
LastTerm(xlog) == IF Len(xlog) = 0 THEN 0 ELSE xlog[Len(xlog)].term

LogIndices == 0..MaxLogIndex

Terms == 0..LargestTerm

AllValues == 1..MaxValue \cup {Nil}

NilEntry == [term |-> 0, value |-> Nil]

ValidEntries == [term: Terms, value: AllValues]

AllEntries == ValidEntries \cup {NilEntry}

\* Return the minimum value from a set, or undefined if the set is empty.
Min(s) == CHOOSE x \in s : \A y \in s : x <= y
\* Return the maximum value from a set, or undefined if the set is empty.
Max(s) == CHOOSE x \in s : \A y \in s : x >= y

----

\* Define initial values for all variables

InitServerVars == /\ currentTerm = [i \in Server |-> 0]
                  /\ state       = [i \in Server |-> Follower]
                  /\ votedFor    = [i \in Server |-> Nil]
InitCandidateVars == /\ votesResponded = [i \in Server |-> {}]
                     /\ votesGranted   = [i \in Server |-> {}]
\* The values nextIndex[i][i] and matchIndex[i][i] are never read, since the
\* leader does not send itself messages. It's still easier to include these
\* in the functions.
InitLeaderVars == /\ nextIndex  = [i \in Server |-> [j \in Server |-> 1]]
                  /\ matchIndex = [i \in Server |-> [j \in Server |-> 0]]
InitLogVars == /\ log          = [i \in Server |-> << >>]
               /\ commitIndex  = [i \in Server |-> 0]
Init == /\ InitServerVars
        /\ InitCandidateVars
        /\ InitLeaderVars
        /\ InitLogVars

----

\* Define state transitions

\* Server i restarts from stable storage.
\* It loses everything but its currentTerm, votedFor, and log.
Restart(i) ==
    /\ state'          = [state EXCEPT ![i] = Follower]
    /\ votesResponded' = [votesResponded EXCEPT ![i] = {}]
    /\ votesGranted'   = [votesGranted EXCEPT ![i] = {}]
    /\ nextIndex'      = [nextIndex EXCEPT ![i] = [j \in Server |-> 1]]
    /\ matchIndex'     = [matchIndex EXCEPT ![i] = [j \in Server |-> 0]]
    /\ commitIndex'    = [commitIndex EXCEPT ![i] = 0]
    /\ UNCHANGED <<currentTerm, votedFor, log>>

\* Server i times out and starts a new election.
Timeout(i) == /\ state[i] \in {Follower, Candidate}
              /\ state' = [state EXCEPT ![i] = Candidate]
              /\ currentTerm' = [currentTerm EXCEPT ![i] = currentTerm[i] + 1]
              \* Most implementations would probably just set the local vote
              \* atomically, but messaging localhost for it is weaker.
              /\ votedFor' = [votedFor EXCEPT ![i] = i]
              /\ votesResponded' = [votesResponded EXCEPT ![i] = {i}]
              /\ votesGranted'   = [votesGranted EXCEPT ![i] = {i}]
              /\ UNCHANGED <<leaderVars, logVars>>

\* Candidate i transitions to leader.
BecomeLeader(i) ==
    /\ state[i] = Candidate
    /\ votesGranted[i] \in Quorum
    /\ state'      = [state EXCEPT ![i] = Leader]
    /\ nextIndex'  = [nextIndex EXCEPT ![i] =
                         [j \in Server |-> Len(log[i]) + 1]]
    /\ matchIndex' = [matchIndex EXCEPT ![i] =
                         [j \in Server |-> 0]]
    /\ UNCHANGED <<currentTerm, votedFor, candidateVars, logVars>>

\* Leader i receives a client request to add v to the log.
ClientRequest(i, v) ==
    /\ state[i] = Leader
    /\ LET entry == [term  |-> currentTerm[i],
                     value |-> v]
           newLog == Append(log[i], entry)
       IN  log' = [log EXCEPT ![i] = newLog]
    /\ UNCHANGED <<serverVars, candidateVars,
                   leaderVars, commitIndex>>

\* Leader i advances its commitIndex.
\* This is done as a separate step from handling AppendEntries responses,
\* in part to minimize atomic regions, and in part so that leaders of
\* single-server clusters are able to mark entries committed.
AdvanceCommitIndex(i) ==
    /\ state[i] = Leader
    /\ LET \* The set of servers that agree up through index.
           Agree(index) == {i} \cup {k \in Server :
                                         matchIndex[i][k] >= index}
           \* The maximum indexes for which a quorum agrees
           agreeIndexes == {index \in 1..Len(log[i]) :
                                Agree(index) \in Quorum}
           \* New value for commitIndex'[i]
           newCommitIndex ==
              IF /\ agreeIndexes /= {}
                 /\ log[i][Max(agreeIndexes)].term = currentTerm[i]
              THEN
                  Max(agreeIndexes)
              ELSE
                  commitIndex[i]
       IN commitIndex' = [commitIndex EXCEPT ![i] = newCommitIndex]
    /\ UNCHANGED <<serverVars, candidateVars, leaderVars, log>>

----

\* Message handler helpers

\* Any RPC with a newer term causes the recipient to advance its term first.
UpdateTerm(i, term) ==
    /\ term > currentTerm[i]
    /\ currentTerm'    = [currentTerm EXCEPT ![i] = term]
    /\ state'          = [state       EXCEPT ![i] = Follower]
    /\ votedFor'       = [votedFor    EXCEPT ![i] = Nil]

----

\* Message handlers
\* i = recipient, j = sender, m = message

\* Server i receives a RequestVote request from server j with
\* m.mterm <= currentTerm[i].
HandleRequestVoteRequest(i,j,lTerm,lIndex,term) == 
    LET cTerm == (IF term > currentTerm[i] THEN term ELSE currentTerm[i])
        cState == (IF term > currentTerm[i] THEN Follower ELSE state[i])
        vFor == (IF term > currentTerm[i] THEN  Nil ELSE votedFor[i])
        logOk ==    \/ lTerm > LastTerm(log[i])
                    \/ /\ lTerm = LastTerm(log[i])
                        /\ lIndex >= Len(log[i])
        grant ==    /\ term = cTerm
                    /\ logOk
                    /\ vFor \in {Nil, j}
    IN  /\ term <= cTerm
        /\  \/ (grant  /\ votedFor' = [votedFor EXCEPT ![i] = j])
            \/ (~grant /\ votedFor' = [votedFor EXCEPT ![i] = vFor])
        /\ state' = [state EXCEPT ![i] = cState]
        /\ currentTerm' = [currentTerm EXCEPT  ![i] = cTerm]
        /\ UNCHANGED <<candidateVars, leaderVars, logVars>>

\* Server i receives a RequestVote response from server j with
\* m.mterm = currentTerm[i].
HandleRequestVoteResponse(i, j, term, grant) ==
    LET cTerm == (IF term > currentTerm[i] THEN term ELSE currentTerm[i])
        cState == (IF term > currentTerm[i] THEN Follower ELSE state[i])
        vFor == (IF term > currentTerm[i] THEN  Nil ELSE votedFor[i])
    IN  /\ term < cTerm => UNCHANGED <<serverVars, candidateVars, leaderVars, logVars>>
        /\  \* This tallies votes even when the current state is not Candidate, but
            \* they won't be looked at, so it doesn't matter.
            /\ term = cTerm
            /\ votesResponded' = [votesResponded EXCEPT ![i] =
                                    votesResponded[i] \cup {j}]
            /\  \/  /\ grant
                    /\ votesGranted' = [votesGranted EXCEPT ![i] =
                                        votesGranted[i] \cup {j}]
                \/  /\ ~grant
                    /\ UNCHANGED <<votesGranted>>
            /\ UNCHANGED <<leaderVars, logVars>>
        /\  IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars

\* Constraints on inputs for an append entries request
ValidAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, entryTerm, cIndex) ==
    /\ i /= j
    /\ pLogTerm < term
    /\  \/ entryTerm = 0
        \/  /\ entryTerm /= 0 
            /\ pLogTerm < entryTerm


\* Server i receives an AppendEntries request from server j with
\* m.mterm <= currentTerm[i]. This handler is invoked when there 
\* are no entries associated with the request
HandleNilAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, cIndex) == 
    /\ ValidAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, 0, cIndex)
    /\  (LET cTerm == (IF term > currentTerm[i] THEN term ELSE currentTerm[i])
            cState == (IF term > currentTerm[i] THEN Follower ELSE state[i])
            vFor == (IF term > currentTerm[i] THEN  Nil ELSE votedFor[i])
            logOk == \/ pLogIndex = 0
                     \/ /\ pLogIndex > 0
                        /\ pLogIndex <= Len(log[i])
                        /\ pLogTerm = log[i][pLogIndex].term
        IN  /\ term <= cTerm
            /\  \/  /\ \* reject request
                        \/  term < cTerm
                        \/  /\ term = cTerm
                            /\ cState = Follower
                            /\ \lnot logOk
                    /\ IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars
                    /\ UNCHANGED logVars
                \/ \* return to follower state
                    /\ term = cTerm
                    /\ cState = Candidate
                    /\ state' = [state EXCEPT ![i] = Follower]
                    /\ IF term > currentTerm[i] THEN (currentTerm' = [currentTerm EXCEPT  ![i] = cTerm] /\ votedFor' = [votedFor EXCEPT ![i] = vFor]) ELSE UNCHANGED <<currentTerm, votedFor>>
                    /\ UNCHANGED logVars
                \/ \* accept request
                    /\ term = cTerm
                    /\ cState = Follower
                    /\ logOk
                    /\  LET index == pLogIndex + 1
                        IN  \* already done with request or
                            \* no conflict: nothing to append since
                            \* cannot have conflict because empty request
                            /\ commitIndex' = [commitIndex EXCEPT ![i] =
                                                    cIndex]
                            /\ IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars
                            /\ UNCHANGED log
            /\ UNCHANGED <<candidateVars, leaderVars>>)

\* Server i receives an AppendEntries request from server j with
\* m.mterm <= currentTerm[i]. This just handles m.entries of length 1, but
\* implementations could safely accept more by treating them the same as
\* multiple independent requests of 1 entry.
HandleAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, entryTerm, entryValue, cIndex) ==
    /\  ValidAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, entryTerm, cIndex)
    /\  (LET cTerm == (IF term > currentTerm[i] THEN term ELSE currentTerm[i])
            cState == (IF term > currentTerm[i] THEN Follower ELSE state[i])
            vFor == (IF term > currentTerm[i] THEN  Nil ELSE votedFor[i])
            logOk == \/ pLogIndex = 0
                     \/ /\ pLogIndex > 0
                        /\ pLogIndex <= Len(log[i])
                        /\ pLogTerm = log[i][pLogIndex].term
             entries == << [term |-> entryTerm, value |-> entryValue] >>
        IN  /\ term <= cTerm
            /\  \/  /\ \* reject request
                        \/  term < cTerm
                        \/  /\ term = cTerm
                            /\ cState = Follower
                            /\ \lnot logOk
                    /\ IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars
                    /\ UNCHANGED logVars
                \/ \* return to follower state
                    /\ term = cTerm
                    /\ cState = Candidate
                    /\ state' = [state EXCEPT ![i] = Follower]
                    /\ IF term > currentTerm[i] THEN (currentTerm' = [currentTerm EXCEPT  ![i] = cTerm] /\ votedFor' = [votedFor EXCEPT ![i] = vFor]) ELSE UNCHANGED <<currentTerm, votedFor>>
                    /\ UNCHANGED logVars
                \/ \* accept request
                    /\ term = cTerm
                    /\ cState = Follower
                    /\ logOk
                    /\ LET index == pLogIndex + 1
                        IN  \/ \* already done with request
                                /\ Len(log[i]) >= index
                                /\ log[i][index].term = entries[1].term
                                    \* This could make our commitIndex decrease (for
                                    \* example if we process an old, duplicated request),
                                    \* but that doesn't really affect anything.
                                /\ commitIndex' = [commitIndex EXCEPT ![i] =
                                                        cIndex]
                                /\ IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars
                                /\ UNCHANGED log
                            \/ \* conflict: remove 1 entry
                                /\ Len(log[i]) >= index
                                /\ log[i][index].term /= entries[1].term
                                /\ LET new == [index2 \in 1..(Len(log[i]) - 1) |->
                                                    log[i][index2]]
                                    IN log' = [log EXCEPT ![i] = new]
                                /\ IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars
                                /\ UNCHANGED commitIndex
                            \/ \* no conflict: append entry
                                /\ Len(log[i]) = pLogIndex
                                /\ log' = [log EXCEPT ![i] =
                                                Append(log[i], entries[1])]
                                /\ IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars
                                /\ UNCHANGED commitIndex
            /\ UNCHANGED <<candidateVars, leaderVars>>)

\* Server i receives an AppendEntries response from server j with
\* m.mterm = currentTerm[i].
HandleAppendEntriesResponse(i, j, term, success, mIndex) ==
    LET cTerm == (IF term > currentTerm[i] THEN term ELSE currentTerm[i])
        cState == (IF term > currentTerm[i] THEN Follower ELSE state[i])
        newMatchIndex == (IF term = cTerm /\ success THEN [matchIndex EXCEPT ![i][j] = mIndex] ELSE matchIndex)
    IN  /\  term < cTerm => UNCHANGED <<candidateVars, leaderVars, logVars>>
        /\  /\ term = cTerm
            /\  \/  /\ success \* successful
                    /\ nextIndex'  = [nextIndex  EXCEPT ![i][j] = mIndex + 1]
                \/  /\ \lnot success \* not successful
                    /\ nextIndex' = [nextIndex EXCEPT ![i][j] =
                                        Max({nextIndex[i][j] - 1, 1})]
            /\  matchIndex' = newMatchIndex
            /\  /\  cState = Leader
                /\  LET \* The set of servers that agree up through index.
                    Agree(index) == {i} \cup {k \in Server :
                                                    newMatchIndex[i][k] >= index}
                    \* The maximum indexes for which a quorum agrees
                    agreeIndexes == {index \in 1..Len(log[i]) :
                                            Agree(index) \in Quorum}
                    \* New value for commitIndex'[i]
                    newCommitIndex ==
                        IF /\ agreeIndexes /= {}
                            /\ log[i][Max(agreeIndexes)].term = cTerm
                        THEN
                            Max(agreeIndexes)
                        ELSE
                            commitIndex[i]
                    IN commitIndex' = [commitIndex EXCEPT ![i] = newCommitIndex]
            /\ UNCHANGED <<candidateVars, log>>
        /\  IF term > currentTerm[i] THEN UpdateTerm(i, term) ELSE UNCHANGED serverVars


\* End of message handlers.

----

\* Abstract transitions

\* Select process i as leader
\* Does not check if a quorum of votes have been received
ElectLeader(i) == 
    /\ state[i] \in {Follower, Candidate}
    /\ state'      = [state EXCEPT ![i] = Leader]
    /\ nextIndex'  = [nextIndex EXCEPT ![i] =
                         [j \in Server |-> Len(log[i]) + 1]]
    /\ matchIndex' = [matchIndex EXCEPT ![i] =
                         [j \in Server |-> 0]]
    /\ UNCHANGED <<currentTerm, votedFor, candidateVars, logVars>>

----

\* Defines how the variables may transition.
Next == \/ \E i \in Server : Restart(i)
        \/ \E i \in Server : Timeout(i)
        \/ \E i \in Server : BecomeLeader(i)
        \/ \E i \in Server : ElectLeader(i)
        \/ \E i \in Server, v \in AllValues : ClientRequest(i, v)
        \/ \E i,j \in Server, term, lTerm \in Terms, lIndex \in LogIndices : HandleRequestVoteRequest(i,j,lTerm,lIndex,term)
        \/ \E i,j \in Server, term \in Terms, grant \in BOOLEAN: HandleRequestVoteResponse(i, j, term, grant)
        \/ \E i,j \in Server, term, pLogTerm \in Terms, pLogIndex, cIndex \in LogIndices : HandleNilAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, cIndex)
        \/ \E i,j \in Server, term,entryTerm,pLogTerm \in Terms, pLogIndex, cIndex \in LogIndices, entryValue \in AllValues : HandleAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, entryTerm, entryValue, cIndex)
        \/ \E i,j \in Server, term \in Terms, success \in BOOLEAN, mIndex \in LogIndices: HandleAppendEntriesResponse(i, j, term, success, mIndex)

Spec == Init /\ [][Next]_vars

----

\* Invariants of the protocol

HaveLeader == \E i \in Server : state[i] = Leader

NoLeader == \A i \in Server : state[i] \in {Follower, Candidate}

\* Only one leader in a given term
OnlyOneLeader == 
    \/ NoLeader
    \/ \A i,j \in Server: (i /= j /\ currentTerm[i] = currentTerm[j] /\ state[i] = Leader) => state[j] /= Leader

LEMMA \A i \in Server: currentTerm[i] <= currentTerm'[i]




===================================================================================