------------------------------ MODULE TwoPhaseCommitAlt ------------------------------

EXTENDS Naturals
CONSTANT RM  \* The set of resource managers
CONSTANT N 

VARIABLES
  rmState,    
  \*  Current request that the TM is deciding on
  tmState

vars == <<rmState, tmState>>

TPInit == 
    /\ rmState = [r \in RM |-> [req |-> 0, state |-> "working"]]
    /\ tmState = 0

NextRequest ==
    /\ (tmState = 0 \/ (rmState = [r \in RM |-> ([req |-> tmState, state |-> "committed"])]))
    /\ tmState' = tmState + 1
    /\ rmState' = [r \in RM |-> [req |-> tmState+1, state |-> "working"]]

RMPrepared(r,i) == 
    /\ rmState[r] = [req |-> i, state |-> "working"]
    /\ rmState' = [rmState EXCEPT  ![r] = [req |-> i, state |-> "prepared"]]
    /\ UNCHANGED tmState

RMAborted(r,i) ==
    /\ rmState[r] = [req |-> i, state |-> "working"]
    /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "aborted"]]
    /\ UNCHANGED tmState

RMCommitted(r, i) ==
    /\ (rmState[r] = [req |-> i, state |-> "working"] \/ rmState[r] = [req |-> i, state |-> "prepared"])
    /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "committed"]]
    /\ UNCHANGED tmState

TPNext == 
    \/ NextRequest
    \/ \E r \in RM, i \in (1..N): RMPrepared(r,i) \/ RMAborted(r, i) \/ RMCommitted(r, i)

TPSpec == TPInit /\ [][TPNext]_vars

----

\* Type consistency checks

TPTypeOK == 
    /\ rmState \in [RM -> [req: (0..N), state: {"working", "prepared", "aborted", "committed"}]]
    /\ tmState \in (0..N)

TCConsistent ==  
  (*************************************************************************)
  (* A state predicate asserting that two RMs have not arrived at          *)
  (* conflicting decisions.                                                *)
  (*************************************************************************)
    \A r1,r2 \in RM: \A i \in (1..N): ~ /\ rmState[r1] = [req |-> i, state |-> "aborted"]
                                        /\ rmState[r2] = [req |-> i, state |-> "committed"]
    

===================================================================================