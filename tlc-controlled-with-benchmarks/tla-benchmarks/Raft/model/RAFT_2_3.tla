---- MODULE RAFT_2_3 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_168061097956010000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_168061097956111000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_168061097956112000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_168061097956113000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_168061097956114000 == 
5
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_168061097956115000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_168061097956116000 == 
2
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_168061097956117000 == 
{1,2,3}
----

=============================================================================
\* Modification History
