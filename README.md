Server Behavior
1. server will maintain a size of the problem it is working on along with the best graph it got so far and the number of clique this graph have (smaller the better).

2. server will listen to a selected port and wait for tcp connection from client. Upon receive the connection, it will start a thread to handle the client


1. LOCK - Send the current size and currentgraph - UNLOCK
   * default current graph is ' '

Server -> current size
Server -> current graph

Loop Start

* Client -> exchange start request message
* Server -> exchange start confirmed message
* Client -> current problem
* Client -> smallest clique size it get

caseA: Client and Server has the same problem size

caseA_0: client's clique size is 0:
* Server -> Request message
* Client -> answer graph
* Server increment the problem size, reset the clique size and graph
* Server -> ProblemSize changed message
* Server -> new problem size


caseA_1: client has smaller number
* Server -> Request message
* Client -> grpah that has the sent clique number

caseA_1.1: received graph is valid
* Server keep the graph

caseA_1.2: received graph is invalid
* Server -> error message


caseA_2: server has smaller number
* Server -> deny message
* Server -> current clique size
* Server -> current graph



caseB: Client and Server has different problem size
* Server -> ProblemSize changed message
* Server -> new problem size
