# Server 
1. server will maintain a size of the problem it is working on along with the best graph it got so far and the number of clique this graph have (smaller the better).

2. server will listen to a selected port and wait for tcp connection from client. Upon receive the connection, it will start a thread to handle the client

3. default current graph is ' '
4. Be aware that all sequential message is send as a long message separated by '\n'

# Server Client Protocol 
            Clinet -> I am a client
            Server -> backup server addr ( ' ' if none )
            Server -> backup server port
            Server -> current size
            Server -> clique size
            Server -> current graph

Loop Start

            Client -> exchange start request message
            Server -> exchange start confirmed message
            Server -> backup server addr ( ' ' if none )
            Server -> backup server port
            Client -> current problem
            Client -> smallest clique size it get
## caseA: Client and Server has the same problem size

### caseA_0: client's clique size is 0:
            Server -> Request message
            Client -> answer graph

#### caseA_0.1: graph is valid
            Server increment the problem size, reset the clique size and graph
            Server -> ProblemSize changed message 
            Server -> new problem size
            Server -> current grpah
            Server -> tranmission complete message

#### caseA_0.2: graph is invalid
            Server -> error message


### caseA_1: client has smaller number
            Server -> Request message
            Client -> grpah that has the sent clique number

#### caseA_1.1: received graph is valid
            Server keep the graph
            Server -> tranmission complete message

#### caseA_1.2: received graph is invalid
            Server -> error message


### caseA_2: server has smaller number
            Server -> deny message
            Server -> current clique size 
            Server -> current graph

### caseA_3: server and client have same clique number
            Server -> deny message + tie message


## caseB: Client and Server has different problem size
            Server -> ProblemSize changed message
            Server -> new problem size
            Server -> clique size + current graph
            Server -> tranmission complete message


# Server to Server

* The  main server  is the server that started first. In order to handle the case that main server died, there will be backup servers too. Backup server connect to main server and notify main server that it is a backup server. 
* Main server will send the backup server's addresses and ports to all of its client. 
* If main server go down, client will try to connect to first candidate backup server instead. 
* Backup server will sync with main server every 2 minutes. 
* Main server will also send backup servers the ip address and ports of other backup servers. 
* When backup server connect to main server, it will know if it is the first candidate backup server. If it is not the first candidate, it will connect to first candidate server and treat that as main server

# Server Server Protocl

Backup -> I am a server
Backup -> backup listenling port

## case C if it is first backup server
            Server -> address of first candidate
            Server -> port of first candidate
            Server -> firstBackup / normalBackup
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> current graph
            Backup -> sync complete

## for sync that happens every 2 minutes
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> first candidate address
            Server -> first candidate port

### case E main server has better clique or problem size changed
            Backup -> graph request
            Server -> current graph
            Backup -> sync complete

### case F clique size and problem size both not change
            Backup -> sync complete
            

# ToDo List

* timeout
* server side handle restart
* client side handle restart
* client side handle main server down
* first candidate backup server handle main server down
* other backup server handle main server down
* all conversion to int might have a error
* main server handle backup server disconnected
