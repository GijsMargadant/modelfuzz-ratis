---- MODULE RAFT_10_5 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_16819156469142000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_16819156469163000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_16819156469164000 == 
20
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_16819156469165000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_16819156469166000 == 
7
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_16819156469167000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_16819156469168000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_16819156469169000 == 
{1,2,3,4,5}
----

=============================================================================
\* Modification History
