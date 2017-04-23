# Server 
1. server will maintain a size of the problem it is working on along with the best graph it got so far and the number of clique this graph have (smaller the better).

2. server will listen to a selected port and wait for tcp connection from client. Upon receive the connection, it will start a thread to handle the client

3.  default current graph is ' '

# Server Client Protocol 
Server -> current size
Server -> clique size
Server -> current graph

Loop Start

* Client -> exchange start request message
* Server -> exchange start confirmed message
* Client -> current problem + smallest clique size it get

## caseA: Client and Server has the same problem size

### caseA_0: client's clique size is 0:
* Server -> Request message
* Client -> answer graph

caseA_0.1: graph is valid
* Server increment the problem size, reset the clique size and graph
* Server -> ProblemSize changed message + new problem size +
            current grpah + tranmission complete message

caseA_0.2: graph is invalid
* Server -> error message


### caseA_1: client has smaller number
* Server -> Request message
* Client -> grpah that has the sent clique number

caseA_1.1: received graph is valid
* Server keep the graph
* Server -> tranmission complete message

caseA_1.2: received graph is invalid
* Server -> error message


### caseA_2: server has smaller number
* Server -> deny message + current clique size + current graph

### caseA_3: server and client have same clique number
* Server -> deny message + tie message


## caseB: Client and Server has different problem size
* Server -> ProblemSize changed message + new problem size + 
            clique size + current graph + tranmission complete message


# Server to Server

* The  main server  is the server that started first. In order to handle the case that main server died, there will be backup servers too. Backup server connect to main server to share its ip address and port. 
* Main server will send the backup server's addresses and ports to all of its client. 
* If main server go down, client will try to connect to first candidate backup server instead. 
* Backup server will sync with main server every 2 minutes. 
* Main server will also send backup servers the ip address and ports of other backup servers. 
* When backup server connect to main server, it will know if it is the first candidate backup server. If it is not the first candidate, it will connect to first candidate server and treat that as main server
