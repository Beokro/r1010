import sys
import socket
import threading
import os
import logging
import getopt
import time

requestMessage = 'request'
denyMessage = 'deny'
tieMessage = 'tie'
errorMessage = 'error'
problemSizeChangedMessage = 'sizeChanged'
restartMessage = 'restart'
exchangeConfirmedMessage = 'confirmed'
exchangeStartMessage = 'start'
tranmissionCompleteMessage = 'complete'
clientClaimMessage = 'claimClient'
serverClaimMessage = 'claimServer'
syncRequestMessage = 'syncReq'
syncCompleteMessage = 'syncCom'
firstBackup = 'first'
normalBackup = 'normal'
backupSyncTime = 5


class TcpServer( object ):
    def __init__( self, host, port, destHost, destPort, timeout, logDir, backup, currentSize ):
        self.host = host
        self.port = port
        self.destHost = destHost
        self.destPort = destPort
        self.timeout = timeout
        self.logDir = logDir
        self.backup = backup
        self.firstCandidateAddr = ' '
        self.firstCandidatePort = -1
        self.myAddr = ' '
        self.myPort = -1
        self.firstCandidate = False
        self.lock = threading.Lock()
        self.currentSize = currentSize
        self.currentGraph = self.defaultGraph()
        self.cliqueSize = 111111111
        self.counter = 0
        self.sock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
        self.sock.setsockopt( socket.SOL_SOCKET, socket.SO_REUSEADDR, 1 )
        self.sock.bind( ( self.host, self.port ) )
        self.liveConnection = 0
        logging.basicConfig( format = '%(asctime)s %(levelname)s: %(message)s',
                             filename = self.logDir,
                             level = logging.DEBUG )
        self.doLogging( 'server run on address: ' + host + ' port: ' + str( port ), '-1' )

    def defaultGraph( self ):
        return '0' * ( currentSize * currentSize )

    def listen( self ):
        self.sock.listen( 200 )
        if self.backup:
            tt = threading.Thread( target = self.contactMainServer )
            tt.daemon = True
            tt.start()
        while True:
            client, address = self.sock.accept()
            # need to be more careful about the timeout
            client.settimeout( 60 )
            self.liveConnection += 1
            self.counter += 1
            t = threading.Thread( target = self.handleClient,
                                  args = ( client, address, str( self.counter ) ) )
            t.daemon = True
            t.start()


    # ********************************************************
    # *********************************************
    # ************************************
    # ***************************
    # ******************
    # *************
    # start of handle server is backup server

    def contactMainServer( self ):
        # create a new sock and connect to server
        backupSock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
        backupSock.settimeout( 60 )
        try:
            backupSock.connect( ( self.destHost, self.destPort ) )
        except:
            print 'connect to main server failed'
            raise Exception( 'main server disconnected' )
            return
        self.handleMainServer( backupSock )

    def handleMainServer( self, backupSock ):
        global firstBackup
        global serverClaimMessage
        global syncRequestMessage
        global syncCompleteMessage

        print 'sent my port to server'
        self.sendPacket( backupSock, [ serverClaimMessage, str( self.port ) ] )
        '''
            Server -> address of first candidate
            Server -> port of first candidate
            Server -> firstBackup / normalBackup
        '''

        data = self.recvPacket( backupSock, 60 )
        self.firstCandidateAddr = data[ 0 ]
        self.firstCandidatePort = int( data[ 1 ] )
        self.firstCandidate = ( data[ 2 ] == firstBackup )
        '''
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> current graph
            Backup -> sync complete
        '''
        self.sendPacket( backupSock, [ syncRequestMessage ] )
        data = self.recvPacket( backupSock, 40 + 850 * 850 )
        self.lock.acquire()

        self.currentSize = int( data[ 0 ] )
        self.cliqueSize = int( data[ 1 ] )
        self.graph = data[ 2 ]

        self.lock.release()
        self.sendPacket( backupSock, [ syncCompleteMessage ] )
        self.doLogging( 'After inital sync, currentSize = ' + str( self.currentSize ) +\
                        ' cliqueSize =' + str( self.cliqueSize ),
                        ' ', isServer = True )
        self.doLogging( 'First candidate addr = ' + self.firstCandidateAddr +\
                        '  First Candiate port = ' + str( self.firstCandidatePort ),
                        ' ', isServer = True )
        try:
            self.handlePeriodicSync( backupSock )
        except:
            self.handleMainServerDown()

    def handlePeriodicSync( self, backupSock ):
        '''
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> first candidate address
            Server -> first candidate port
        '''
        global backupSyncTime
        while True:
            time.sleep( backupSyncTime )
            self.doLogging( 'periodic sync start', ' ', isServer = True )
            global syncRequestMessage
            global syncCompleteMessage

            self.sendPacket( backupSock, [ syncRequestMessage ] )
            data = self.recvPacket( backupSock, 80 )
            self.lock.acquire()

            currentSize = int( data[ 0 ] )
            cliqueSize = int( data[ 1 ] )
            self.firstCandidateAddr = data[ 2 ]
            self.firstCandidatePort = int( data[ 3 ] )

            if self.currentSize != currentSize or self.cliqueSize > cliqueSize:
                self.currentSize = currentSize
                self.cliqueSize = cliqueSize
                self.sendPacket( backupSock, [ requestMessage ] )
                data = self.recvPacket( backupSock, self.currentSize * currentSize + 10 )
                self.currentGraph = data[ 0 ]

            self.sendPacket( backupSock, [ syncCompleteMessage ] )
            self.doLogging( 'currentSize = ' + str( self.currentSize ) +\
                            ' cliqueSize =' + str( self.cliqueSize ),
                            ' ', isServer = True )

            self.lock.release()
            self.doLogging( 'periodic sync complete', ' ', isServer = True )

    def handleMainServerDown( self ):
        try:
            # try to reconnect to server
            self.lock.release()
            self.contactMainServer()
        except:
            # reconnect to first candidate server this backup is not first candidate
            self.connectToFirstBackup()

    def connectToFirstBackup( self ):
        if self.firstCandidate:
            self.firstCandidateAddr = ' '
            print 'I am first candidate, wait for client connection'
            return
        self.destHost = self.firstCandidateAddr
        self.destPort = self.firstCandidatePort
        self.contactMainServer()
    # end of handle server is backup server












    def handleClient( self, client, address, clientID ):
        print 'here'
        self.doLogging( 'new connection establish', clientID )
        # send my currentSize and graph to the clinet to start the computation
        self.lock.acquire()
        data = self.recvPacket( client, 20 )
        if data[ 0 ] == clientClaimMessage:
            print 'it is a client'
            self.handleClientToServer( client, address, clientID )
        else:
            print 'it is a server'
            self.handleServerToServer( client, address, clientID, data[ 1 ] )

    # ********************************************************
    # *********************************************
    # ************************************
    # ***************************
    # ******************
    # *************
    # start of handle server to server

    def handleServerToServer( self, client, address, clientID, listeningPort ):
        global syncRequestMessage
        global syncCompleteMessage
        global firstBackup
        global normalBackup
        self.doLogging( ' is a server', clientID )
        self.lock.release()
        '''
            Server -> address of first candidate
            Server -> port of first candidate
            Server -> firstBackup / normalBackup
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> current graph
            Backup -> sync complete
        '''
        firstCandidateResponse = normalBackup
        if self.firstCandidateAddr == ' ':
            self.firstCandidateAddr = address[ 0 ]
            self.firstCandidatePort = listeningPort
            print listeningPort
            firstCandidateResponse = firstBackup

        self.sendPacket( client, [ self.firstCandidateAddr,
                                   str( self.firstCandidatePort ),
                                        firstCandidateResponse ] )
        data = self.recvPacket( client, 20 )[ 0 ]
        if data != syncRequestMessage:
            self.handleUnexpectMessage( client, syncRequestMessage, data, clientID, True )
            return

        self.lock.acquire()
        self.sendPacket( client, [ str( self.currentSize ),
                                   str( self.cliqueSize ),
                                   str( self.currentGraph ) ] )
        self.lock.release()
        data = self.recvPacket( client, 20 )[ 0 ]
        if data != syncCompleteMessage:
            self.handleUnexpectMessage( client, syncCompleteMessage, data, clientID, True )
            return

        while True:
            try:
                data = self.recvPacket( client, 20 )[ 0 ]
                if data:
                    self.doLogging( 'data sync start', clientID, isServer = True )
                    self.handleBackupSync( data, client, clientID )
                else:
                    self.doLogging(  'backup server disconnected', clientID, 'warning' )
                    # to do, handle backup server disconnect
                    raise Exception( 'backup server disconnected' )
            except Exception as e:
                exc_type, exc_obj, exc_tb = sys.exc_info()
                fname = os.path.split( exc_tb.tb_frame.f_code.co_filename ) [ 1 ]
                print ( exc_type, fname, exc_tb.tb_lineno )
                client.close()
                return


    def handleBackupSync( self, data, client, clientID ):
        global syncRequestMessage
        global syncCompleteMessage
        global requestMessage
        '''
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> first candidate address
        '''
        if data != syncRequestMessage:
            self.handleUnexpectMessage( client, syncRequestMessage, data, clientID, True )
            return
        self.lock.acquire()
        self.sendPacket( client, [ str( self.currentSize ),
                                   str( self.cliqueSize ),
                                   self.firstCandidateAddr,
                                   str( self.firstCandidatePort ) ] )
        self.lock.release()
        data = self.recvPacket( client, 20 )[ 0 ]

        '''
            Backup -> graph request
            Server -> current graph
            Backup -> sync complete
        '''
        if data == requestMessage:
            self.lock.acquire()
            self.sendPacket( client, [ self.currentGraph ] )
            self.lock.release()
            data = self.recvPacket( client, 20 )[ 0 ]

        '''
            Backup -> sync complete
        '''
        if data != syncCompleteMessage:
            self.handleUnexpectMessage( client, syncCompleteMessage, data, clientID, True )
            return
        self.doLogging( 'data sync complete', clientID, isServer = True )

    # end of handle server is backup server


    # ********************************************************
    # *********************************************
    # ************************************
    # ***************************
    # ******************
    # *************
    # start of handle client to server


    def handleClientToServer( self, client, address, clientID ):
        self.doLogging( ' is a client', clientID )
        self.sendPacket( client, [ self.firstCandidateAddr,
                                   str( self.firstCandidatePort ),
                                   str( self.currentSize ),
                                   str( self.cliqueSize ),
                                   self.currentGraph ] )
        self.lock.release()
        while True:
            try:
                data = self.recvPacket( client, 20 )[ 0 ]
                if data:
                    self.doLogging( 'data exchange start', clientID )
                    self.handleClique( data, client, clientID )
                else:
                    self.doLogging(  'client disconnected', clientID, 'warning' )
                    self.liveConnection -= 1
                    self.doLogging(  'remaining connection # = ' + str( self.liveConnection ),
                                     clientID )
                    raise Exception( 'Client disconnected' )
            except Exception as e:
                exc_type, exc_obj, exc_tb = sys.exc_info()
                fname = os.path.split( exc_tb.tb_frame.f_code.co_filename ) [ 1 ]
                print ( exc_type, fname, exc_tb.tb_lineno )
                client.close()
                return


    def handleClique( self, data, client, clientID ):
        global exchangeStartMessage
        global exchangeConfirmedMessage
        # matrix received is at most size * size big, give some extra jic
        recvSize = 0
        clientProblemSize = -1
        clientCliqueSize = -1
        message = ''

        # make sure the data server receiviing is what it is expecting
        if data != exchangeStartMessage:
            self.handleUnexpectMessage( client, exchangeStartMessage, data, clientID )
            return
        self.sendPacket( client, [ exchangeConfirmedMessage,
                                   self.firstCandidateAddr,
                                   str( self.firstCandidatePort ) ] )

        # check if server and client have the same problem size
        datas = self.recvPacket( client, 45 )
        clientProblemSize = int( datas[ 0 ] )
        clientCliqueSize = int ( datas[ 1 ] )
        self.doLogging( 'client has problem size: ' + str( clientProblemSize ) +\
                        ' clique: ' + str( clientCliqueSize ), clientID )

        self.lock.acquire()

        # case B
        if clientProblemSize != self.currentSize:
            self.handleDifferentProblemSize( client, clientID )
        # case A_1, A_0
        elif clientCliqueSize < self.cliqueSize:
            self.requestAndHandleNewGraph( client, clientCliqueSize, clientID )
        # case A_2
        elif clientCliqueSize > self.cliqueSize:
            self.denyNewGraph( client, False, clientID )
        # case A_3
        else:
            self.denyNewGraph( client, True, clientID )
        self.lock.release()

    def handleUnexpectMessage( self, client, expecting, received, clientID, isServer = False ):
        global restartMessage
        self.doLogging( 'received unexpected message', clientID, 'warning', isServer )
        self.doLogging( 'expecting ' + expecting, clientID, 'warning', isServer )
        self.doLogging( 'received ' + received, clientID, 'warning', isServer )
        self.sendPacket( client, [ restartMessage ] )
        return

    def requestAndHandleNewGraph( self, client, clientCliqueSize, clientID ):
        global requestMessage
        global errorMessage
        global tranmissionCompleteMessage
        graph = ' '
        recvSize = self.currentSize * self.currentSize + 10

        # request the matrix from the client
        self.doLogging( 'request graph from client', clientID )
        self.sendPacket( client, [ requestMessage ] )
        graph = self.recvPacket( client, recvSize )[ 0 ]

        # case A_0.2 & case A_1.2, invalid graph
        if not self.validGraph( graph, clientID ):
            self.doLogging( 'graph from client is invalid', clientID, 'warning' )
            self.sendPacket( client, [ errorMessage ] )
            return

        # case A_0.1 && case A_1.1, keep the graph
        self.currentGraph = graph
        self.cliqueSize = clientCliqueSize

        # case A_0.1, increment the problem size, include tranmission complete message
        if clientCliqueSize == 0:
            self.currentSize += 1
            self.currentGraph = self.defaultGraph()
            self.cliqueSize = 111111111
            self.doLogging( 'answer found, update problem size', clientID )
            self.cleanLogFile()
            self.handleDifferentProblemSize( client, clientID )
        # case A_1.1, tranmission complete
        else:
            self.doLogging( 'exchange complete', clientID )
            self.sendPacket( client, [ tranmissionCompleteMessage ] )

    def denyNewGraph( self, client, tie, clientID ):
        # deny the matrix, not need to send if it is worse than current one
        # instead server will send its graph to client
        global denyMessage
        global tieMessage
        if tie:
            self.doLogging( 'server and client has the same clique number', clientID )
            self.sendPacket( client, [ denyMessage, tieMessage ] )
        else :
            self.doLogging( 'server has better graph, send it to client', clientID )
            self.sendPacket( client, [ denyMessage, str( self.cliqueSize ), str( self.currentGraph ) ] )

    def validGraph( self, graph, clientID ):
        # don't need to lock here, if size has been changed
        # no need to process the request from client anyway
        if len( graph ) != self.currentSize * self.currentSize:
            self.doLogging( 'graph received from client has wrong length', clientID, 'warning' )
            self.doLogging( graph, clientID, 'warning' )
            return False
        for g in graph:
            if g != '0' and g != '1':
                self.doLogging( 'graph contained invalid character' + g, clientID, 'warning' )
                self.doLogging( graph, clientID, 'warning' )
                return False
        return True

    # handle case B
    def handleDifferentProblemSize( self, client, clientID ):
        global problemSizeChangedMessage
        self.doLogging( 'problem sized not matched, start to sync', clientID, 'warning' )
        datas = [ problemSizeChangedMessage,
                  str( self.currentSize ),
                  str( self.cliqueSize ),
                  self.currentGraph,
                  tranmissionCompleteMessage ]
        self.sendPacket( client, datas )
        return

    # end of handle client to server


    # take care of sending multiple data at once
    def sendPacket( self, client, datas ):
        message = ''
        for data in datas:
            message += data + '\n'
        client.send( message )

    # take care of receiving and split the data inside packet
    def recvPacket( self, client, size ):
        data = client.recv( size )
        return data.split( '\n' )

    def cleanLogFile( self ):
        # log file can hold logs for at most 10 problems
        if self.currentSize % 10 == 0:
            open( self.logDir, 'w' ).close()

    def doLogging( self, message, clientID, level = 'info', isServer = False ):
        if isServer and not self.backup:
            otherSide = 'server'
        elif isServer:
            otherSide = 'main server'
        else:
            otherSide = 'client'
        if level == 'info':
            logging.info( otherSide + clientID + ': ' + message )
        else:
            logging.warning( otherSide + clientID + ': ' + message )


def usage():
    print 'python server.py [-h] [-p portnumber] [-t timeout] [-l logDir] [-a destAddr] [-d destPort] [-b]'


if __name__ == "__main__":
    try:
        opts, args = getopt.getopt( sys.argv[ 1: ], "p:ht:l:a:d:bc:",
                                    [ "port=", "help", "timeout=", "log=",
                                      "addrdest=", "destport", "backup", "currentSize=" ] )
    except getopt.GetoptError as err:
        print str( err )
        usage()
        sys.exit( 2 )

    port = 7788
    timeout = 60
    logDir = 'server.log'
    destIP = '0.0.0.0'
    destPort = 7788
    backup = False
    currentSize = 5

    for o, a in opts:
        if o in ( "-h", "--help" ):
            usage()
            sys.exit()
        elif o in ( "-p", "--port" ):
            port = int( a )
        elif o in ( "-t", "--timeout" ):
            timeout = int( a )
        elif o in ( "-l", "--log" ):
            logDir = a
        elif o in ( "-a", "--addrdest" ):
            destIP = a
        elif o in ( "-d", "--destport" ):
            destPort = int( a )
        elif o in ( "-b", "--backup" ):
            backup = True
        elif o in ( "-c", "--currentSize" ):
            currentSize = int( a )
        else:
            assert False, "unhandled option"

    if backup and logDir == 'server.log':
        logDir = 'backup.log'
    try:
        temp = TcpServer( '', port, destIP, destPort, timeout, logDir, backup, currentSize )
        temp.listen()
    except KeyboardInterrupt:
        print '^C received, shutting down the web server'
        temp.sock.close()
        exit()
