---- MODULE RAFT_1_3 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_168011158880918000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_168011158880919000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_168011158880920000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_168011158880921000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_168011158880922000 == 
3
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_168011158880923000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_168011158880924000 == 
1
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_168011158880925000 == 
{1,2,3}
----

=============================================================================
\* Modification History
