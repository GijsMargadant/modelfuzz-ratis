---- MODULE RAFT_10_3 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_16835471491792000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_16835471491803000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_16835471491804000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_16835471491805000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_16835471491806000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_16835471491807000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_16835471491808000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_16835471491809000 == 
{1,2,3}
----

=============================================================================
\* Modification History
