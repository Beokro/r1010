Server Behavior
1. server will maintain a size of the problem it is working on along with the best graph it got so far and the number of clique this graph have (smaller the better).

2. server will listen to a selected port and wait for tcp connection from client. Upon receive the connection, it will start a thread to handle the client


1. LOCK - Send the current size and currentgraph - UNLOCK
   * default current graph is ' '

Server -> current size
Server -> clique size
Server -> current graph

Loop Start

* Client -> exchange start request message
* Server -> exchange start confirmed message
* Client -> current problem + smallest clique size it get

caseA: Client and Server has the same problem size

caseA_0: client's clique size is 0:
* Server -> Request message
* Client -> answer graph

caseA_0.1: graph is valid
* Server increment the problem size, reset the clique size and graph
* Server -> ProblemSize changed message + new problem size +
            current grpah + tranmission complete message

caseA_0.2: graph is invalid
* Server -> error message


caseA_1: client has smaller number
* Server -> Request message
* Client -> grpah that has the sent clique number

caseA_1.1: received graph is valid
* Server keep the graph
* Server -> tranmission complete message

caseA_1.2: received graph is invalid
* Server -> error message


caseA_2: server has smaller number
* Server -> deny message + current clique size + current graph

caseA_3: server and client have same clique number
* Server -> deny message + tie message


caseB: Client and Server has different problem size
* Server -> ProblemSize changed message + new problem size + 
            clique size + current graph + tranmission complete message
