# Server 
1. server will maintain a size of the problem it is working on along with the best graph it got so far and the number of clique this graph have (smaller the better).

2. server will listen to a selected port and wait for tcp connection from client. Upon receive the connection, it will start a thread to handle the client

3. default current graph is ' '
4. default map is ' '
5. default cliquesize is long_max
6. defualt port number is 7788
7. server will read from tempFile and use that graph at start up unless server use -n flag. If -n flag is used, user must also use -c flag follow with a number to tell server the currentSize.
8. be aware that all sequential message is send as a long message separated by '\n'

# Usage

* python server.py [-h] [-p portnumber] [-t timeout] [-l logDir] [-a destAddr] [-d destPort] [-b] [-c currentsize] [-n]
* -h               help, print out the usage of server
* -p portNumber    port that server will listen for incoming connection
* -t timeout       set the timeout of server, not implemented
* -l logDir        change the log file name, defualt for main server is server.log. Default for backup server is backup.log
* -a destAddr      main server's ip address, only used if server is a backup server
* -d destPort      main server's port, only used if server is a backup server
* -b               backup server
* -c currentSize   specifiy the currentsize of the server, only used if -n flag is used
* -n               not read from tempfile flag. must be used with -c flag

# Important files

* answer - record all of the answers found
* newAnswer - only record the most recent answer
* tempFile - record the graph that user currently working on

# Server Client Protocol 
            Clinet -> I am a client
            Server -> backup server addr ( ' ' if none )
            Server -> backup server port
            Server -> current size
            Server -> clique size
            Server -> current graph
            Server -> isMapValid
            Server -> map

Alpha Request
            Client -> Alpha Request message
            Server -> Alpha

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
            Server -> isMapValid
            Server -> current map
            Server -> tranmission complete message

#### caseA_0.2: graph is invalid
            Server -> error message


### caseA_1: client has smaller number
            Server -> Request message
            Client -> grpah that has the sent clique number
            Client -> map corresponding with that

#### caseA_1.1: received graph is valid
            Server keep the graph
            Server -> tranmission complete message

#### caseA_1.2: received graph is invalid
            Server -> error message


### caseA_2: server has smaller number
            Server -> deny message
            Server -> current clique size 
            Server -> current graph
            Server -> isMapValid
            Server -> current map

### caseA_3: server and client have same clique number
            Server -> deny message + tie message


## caseB: Client and Server has different problem size
            Server -> ProblemSize changed message
            Server -> new problem size
            Server -> clique size + current graph
            Server -> isMapValid
            Server -> current map
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
            Server -> lastResult
            Server -> lastGraph
            Backup -> sync complete

## for sync that happens every 2 minutes
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> first candidate address
            Server -> first candidate port

### case E main server has better clique
            Backup -> graph request
            Server -> current graph
            Backup -> sync complete

### case F clique size and problem size both not change
            Backup -> sync complete

### case G main server problem size changed
            Backup -> graph request
            Server -> current graph
            Backup -> last answer request
            Server -> last size
            Server -> last graph
            Backup -> sync complete
            

# ToDo List

* server side handle restart
* client side handle restart
* first candidate backup server handle main server down
* other backup server handle main server down
* all conversion to int might have a error
* main server handle backup server disconnected
