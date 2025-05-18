------------------------------ MODULE TwoPhaseCommit ------------------------------

(***************************************************************************)
(* This specification is a modified version of the "Two-Phase Commit",     *)
(* in Section 6 of the TLA+ Video Course.  				   *)
(* It modifies the model with a parametrised number of client requests and *) 
(* some changes in the message exchanges.                                  *)
(***************************************************************************)
EXTENDS Naturals
CONSTANT RM  \* The set of resource managers
CONSTANT N   \* The number of requests

VARIABLES
  curRequest,
  rmState,       \* rmState[r] is the state of resource manager r.
  tmState,       \* The state of the transaction manager.
  tmPrepared,    \* The set of RMs from which the TM has received "Prepared" (* *)
                 \* messages.
  tmCommitted,   \* The set of request ids TM has committed.
  msgs           
    (***********************************************************************)
    (* In the protocol, processes communicate with one another by sending  *)
    (* messages.  For simplicity, we represent message passing with the    *)
    (* variable msgs whose value is the set of all messages that have been *)
    (* sent.  A message is sent by adding it to the set msgs.  An action   *)
    (* that, in an implementation, would be enabled by the receipt of a    *)
    (* certain message is here enabled by the presence of that message in  *)
    (* msgs.  For simplicity, messages are never removed from msgs.  This  *)
    (* allows a single message to be received by multiple receivers.       *)
    (* Receipt of the same message twice is therefore allowed; but in this *)
    (* particular protocol, that's not a problem.                          *)
    (***********************************************************************)

Messages ==
  (*************************************************************************)
  (* The set of all possible messages.  Messages of type "Prepared" are    *)
  (* sent from the RM indicated by the message's rm field to the TM.       *)
  (* Messages of type "Commit" and "Abort" are broadcast by the TM, to be  *)
  (* received by all RMs.  The set msgs contains just a single copy of     *)
  (* such a message.                                                       *)
  (*************************************************************************)
  [type : {"RMPrepared", "RMAborted"}, rm : RM, req : (1..N)]  \cup  [type : {"Prepare", "Commit", "Abort"}, req : (1..N)]
   
    
TPTypeOK ==  
  (*************************************************************************)
  (* The type-correctness invariant                                        *)
  (*************************************************************************)
  /\ rmState \in [RM -> [req : (0 .. N), state: {"init", "working", "prepared", "committed", "aborted"}]]
  /\ tmPrepared \subseteq RM
  /\ msgs \subseteq Messages
  /\ tmCommitted \subseteq (1 .. N)
  /\ curRequest \in (0 .. N)
  /\ tmState \in {"init", "done"}


TPInit ==   
  (*************************************************************************)
  (* The initial predicate.                                                *)
  (*************************************************************************)
  /\ rmState =  [r \in RM |-> [req |-> 0, state |-> "init"]]
  (* TM moves to init state if it takes a request to run a transaction *)
  /\ tmState =  "done"
  /\ tmPrepared   = {}
  /\ tmCommitted   = {}
  /\ msgs = {}
  /\ curRequest = 0
  
-----------------------------------------------------------------------------
(***************************************************************************)
(* We now define the actions that may be performed by the processes, first *)
(* the TM's actions, then the RMs' actions.                                *)
(***************************************************************************)


TMSendPrepareReq(i) == 
   /\ i = curRequest
   /\ (tmState = "init") 
   /\ msgs' = msgs \cup {[type |-> "Prepare", req |-> i]}
   /\ UNCHANGED <<rmState, tmState, tmPrepared, tmCommitted, curRequest>>


RMRcvPrepareReq(r, i) == 
  /\ rmState[r] = [req |-> i, state |-> "init"]
  /\ [type |-> "Prepare", req |-> i] \in msgs
  /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "working"]]
  /\ UNCHANGED <<tmState, tmPrepared, tmCommitted, msgs, curRequest>>


RMSendPrepared(r, i) == 
  (*************************************************************************)
  (* Resource manager r prepares request i.                                *)
  (*************************************************************************)
  (*/\ i = curRequest*)
  /\ rmState[r] = [req |-> i, state |-> "working"]
  /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "prepared"]]
  /\ msgs' = msgs \cup {[type |-> "RMPrepared", rm |-> r, req |-> i]}
  /\ UNCHANGED <<tmState, tmPrepared, tmCommitted, curRequest>>
  

RMSendAborted(r, i) ==
  (*************************************************************************)
  (* Resource manager r spontaneously decides to abort.  As noted above, r *)
  (* does not send any message in our simplified spec.                     *)
  (*************************************************************************)
  (*/\ i = curRequest *)
  /\ (rmState[r] = [req |-> i, state |-> "working"] \/ rmState[r] = [req |-> i, state |-> "init"])
  /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "aborted"]]
  /\ msgs' = msgs \cup {[type |-> "RMAborted", rm |-> r, req |-> i]} \* Sends an abort message to the TM
  /\ UNCHANGED <<tmState, tmPrepared, tmCommitted, curRequest>>
  
  
RMRcvGlobalAbort(r, i) ==
  (*************************************************************************)
  (* Resource manager r is told by the TM to abort.                        *)
  (*************************************************************************)
  (*/\ i = curRequest *)
  /\ rmState[r] = [req |-> i, state |-> "working"]
  /\ [type |-> "Abort", req |-> i] \in msgs
  /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "aborted"]]
  /\ UNCHANGED <<tmState, tmPrepared, tmCommitted, msgs, curRequest>>


RMRcvGlobalCommit(r, i) ==
  (*************************************************************************)
  (* Resource manager r is told by the TM to commit.                       *)
  (*************************************************************************)
  (*/\ i = curRequest *)
  /\ (rmState[r] = [req |-> i, state |-> "working"] \/ rmState[r] = [req |-> i, state |-> "prepared"])
  /\ [type |-> "Commit", req |-> i] \in msgs
  /\ rmState' = [rmState EXCEPT ![r] = [req |-> i, state |-> "committed"]]
  /\ UNCHANGED <<tmState, tmPrepared, tmCommitted, msgs, curRequest>>

    
TMRcvPrepared(r, i) ==
  (*************************************************************************)
  (* The TM receives a "Prepared" message from resource manager r.  We     *)
  (* could add the additional enabling condition r \notin tmPrepared,      *)
  (* which disables the action if the TM has already received this         *)
  (* message.  But there is no need, because in that case the action has   *)
  (* no effect; it leaves the state unchanged.                             *)
  (*************************************************************************)
  /\ i = curRequest
  /\ tmState = "init"
  /\ [type |-> "RMPrepared", rm |-> r, req |-> i] \in msgs
  /\ tmPrepared' = tmPrepared \cup {r}
  /\ UNCHANGED <<rmState, tmState, tmCommitted, msgs, curRequest>>
  
  
TMRcvAborted(r, i) ==
  (*************************************************************************)
  (* The TM receives an "Aborted" message from resource manager r.  We     *)
  (* could add the additional enabling condition r \notin tmPrepared,      *)
  (* which disables the action if the TM has already received this         *)
  (* message.  But there is no need, because in that case the action has   *)
  (* no effect; it leaves the state unchanged.                             *)
  (*************************************************************************)
  /\ i = curRequest 
  /\ tmState = "init"
  /\ tmState' = "done"
  /\ [type |-> "RMAborted", rm |-> r, req |-> i] \in msgs
  /\ msgs' = msgs \cup {[type |-> "Abort", req |-> i]}
  /\ UNCHANGED <<rmState, tmPrepared, tmCommitted, curRequest>>

    
TMSendGlobalCommit(i) ==
  (*************************************************************************)
  (* The TM commits the transaction; enabled iff the TM is in its initial  *)
  (* state and every RM has sent a "Prepared" message.                     *)
  (*************************************************************************)
  /\ i = curRequest 
  /\ tmState = "init"
  /\ tmPrepared = RM
  /\ tmState' = "done"
  /\ tmCommitted' = tmCommitted \cup {i}
  /\ msgs' = msgs \cup {[type |-> "Commit", req |-> i]}
  /\ UNCHANGED <<rmState, tmPrepared, curRequest>>
    
NextRequest ==   
    /\ tmState = "done"
    /\ curRequest < N
    (* The next request is sent only if the previous one is committed *)
    /\ ( curRequest = 0 \/ ( rmState =  [r \in RM |-> ([req |-> curRequest, state |-> "committed"])] ))
    /\ tmState' = "init"
    /\ tmPrepared' = {}
    /\ rmState' =  [r \in RM |-> [req |-> curRequest + 1, state |-> "init"]]
    /\ curRequest' =  curRequest + 1 
    /\ UNCHANGED <<tmCommitted, msgs>>
    
    
TPNext == 
    \/ NextRequest
    \/ \E r \in RM,  i \in (1..N): 
        TMSendPrepareReq(i) \/ TMRcvPrepared(r, i) \/ TMRcvAborted(r, i) \/ TMSendGlobalCommit(i)
        \/ RMRcvPrepareReq(r, i) \/ RMSendPrepared(r, i) \/ RMSendAborted(r, i) \/ RMRcvGlobalAbort(r, i) \/ RMRcvGlobalCommit(r, i)


-----------------------------------------------------------------------------
(***************************************************************************)
(* The material below this point is not discussed in Video Lecture 6.  It  *)
(* will be explained in Video Lecture 8.                                   *)
(***************************************************************************)

TPSpec == TPInit /\ [][TPNext]_<<rmState, tmState, tmPrepared, tmCommitted, msgs, curRequest>>
  (*************************************************************************)
  (* The complete spec of the Two-Phase Commit protocol.                   *)
  (*************************************************************************)

(*THEOREM TPSpec => []TPTypeOK*)
  (*************************************************************************)
  (* This theorem asserts that the type-correctness predicate TPTypeOK is  *)
  (* an invariant of the specification.                                    *)
  (*************************************************************************)
-----------------------------------------------------------------------------
(***************************************************************************)
(* We now assert that the Two-Phase Commit protocol implements the         *)
(* Transaction Commit protocol of module TCommit.  The following statement *)
(* imports all the definitions from module TCommit into the current        *)
(* module.                                                                 *)
(***************************************************************************)
TCConsistent ==  
  (*************************************************************************)
  (* A state predicate asserting that two RMs have not arrived at          *)
  (* conflicting decisions.  It is an invariant of the specification.      *)
  (*************************************************************************)
  \A r1, r2 \in RM: \A i \in (1..N): ~ /\ rmState[r1] = [req |-> i, state |-> "aborted"]
                                            /\ rmState[r2] = [req |-> i, state |-> "committed"]

(* The property below is used for reachability checking purposes, it is not part of the model *)
(* TCExists == \A r1 \in RM: rmState[r1] # [req |-> 5, state |-> "aborted"] *)

THEOREM TPSpec => [](TPTypeOK /\ TCConsistent)
  (*************************************************************************)
  (* This theorem asserts that the specification TPSpec of the Two-Phase   *)
  (* Commit protocol implements the specification TCSpec of the            *)
  (* Transaction Commit protocol.                                          *)
  (*************************************************************************)
(***************************************************************************)
(* The two theorems in this module have been checked with TLC for six      *)
(* RMs, a configuration with 50816 reachable states, in a little over a    *)
(* minute on a 1 GHz PC.                                                   *)
(***************************************************************************)
=============================================================================
\* Modification History
\* Last modified Fri Feb 24 10:40:27 CET 2023 by burcuozkan
\* Created Tue Jan 03 11:48:54 CET 2023 by burcuozkan
