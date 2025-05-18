---- MODULE RAFT_5_5 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_16823648380242000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_16823648380253000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_16823648380254000 == 
14
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_16823648380255000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_16823648380256000 == 
6
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_16823648380257000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_16823648380258000 == 
5
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_16823648380259000 == 
{1,2,3,4,5}
----

=============================================================================
\* Modification History
