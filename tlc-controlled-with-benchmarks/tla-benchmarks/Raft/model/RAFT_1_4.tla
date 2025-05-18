---- MODULE RAFT_1_4 ----
EXTENDS raft_enhanced, TLC

\* CONSTANT definitions @modelParameterConstants:0Follower
const_16806109467462000 == 
"follower"
----

\* CONSTANT definitions @modelParameterConstants:1Leader
const_16806109467473000 == 
"leader"
----

\* CONSTANT definitions @modelParameterConstants:2LargestTerm
const_16806109467474000 == 
10
----

\* CONSTANT definitions @modelParameterConstants:3Nil
const_16806109467475000 == 
0
----

\* CONSTANT definitions @modelParameterConstants:4MaxLogIndex
const_16806109467476000 == 
4
----

\* CONSTANT definitions @modelParameterConstants:5Candidate
const_16806109467477000 == 
"candidate"
----

\* CONSTANT definitions @modelParameterConstants:6MaxValue
const_16806109467478000 == 
1
----

\* CONSTANT definitions @modelParameterConstants:7Server
const_16806109467479000 == 
{1,2,3,4}
----

=============================================================================
\* Modification History
