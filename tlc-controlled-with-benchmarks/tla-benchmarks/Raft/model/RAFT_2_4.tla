---- MODULE RAFT_2_4 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_168061101273118000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_168061101273119000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_168061101273120000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_168061101273121000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_168061101273122000 == 
5
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_168061101273123000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_168061101273124000 == 
2
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_168061101273125000 == 
{1,2,3,4}
----

=============================================================================
\* Modification History
