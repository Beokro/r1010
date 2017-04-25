import sys
import socket
import threading
import os
import logging

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
logFileName = 'server.log'


class TcpServer( object ):
    def __init__( self, host, port, currentSize ):
        global logFileName
        self.host = host
        self.port = port
        self.lock = threading.Lock()
        self.currentSize = currentSize
        self.currentGraph = ' '
        self.cliqueSize = 111111111
        self.counter = 0
        self.backupAddr = ' '
        self.sock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
        self.sock.setsockopt( socket.SOL_SOCKET, socket.SO_REUSEADDR, 1 )
        self.sock.bind( ( self.host, self.port ) )
        logging.basicConfig( format = '%(asctime)s %(levelname)s: %(message)s',
                             filename = logFileName,
                             level = logging.DEBUG )

    def listen( self ):
        self.sock.listen( 5 )
        while True:
            client, address = self.sock.accept()
            # need to be more careful about the timeout
            # client.settimeout( 60 )
            self.counter += 1
            t = threading.Thread( target = self.handleClient,
                                  args = ( client, address, str( self.counter ) ) )
            t.daemon = True
            t.start()

    def handleClient( self, client, address, clientID ):
        self.doLogging( 'new connection establish', clientID )
        # send my currentSize and graph to the clinet to start the computation
        self.lock.acquire()
        data = self.recvPacket( client, 20 )[ 0 ]
        if data == clientClaimMessage:
            self.handleClientToServer( client, address, clientID )
        else:
            self.handleServerToServer( client, address, clientID )


    def handleClientToServer( self, client, address, clientID ):
        self.doLogging( ' is a client', clientID )
        self.sendPacket( client, [ self.backupAddr,
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
                    raise error( 'Client disconnected' )
            except Exception as e:
                exc_type, exc_obj, exc_tb = sys.exc_info()
                fname = os.path.split( exc_tb.tb_frame.f_code.co_filename ) [ 1 ]
                print ( exc_type, fname, exc_tb.tb_lineno )
                client.close()
                return

    def handleServerToServer( self, client, address, clientID ):
        self.doLogging( ' is a server', clientID )
        self.lock.release()

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
        self.sendPacket( client, [ exchangeConfirmedMessage, self.backupAddr ] )

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

    def handleUnexpectMessage( self, client, expecting, received, clientID ):
        global restartMessage
        self.doLogging( 'received unexpected message', clientID, 'warning' )
        self.doLogging( 'expecting ' + expecting, clientID, 'warning' )
        self.doLogging( 'received ' + received, clientID, 'warning' )
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
            self.currentGraph = ' '
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
        global logFileName
        # log file can hold logs for at most 10 problems
        if self.currentSize % 10 == 0:
            open( logFileName, 'w' ).close()

    def doLogging( self, message, clientID, level = 'info' ):
        if level == 'info':
            logging.info( 'client' + clientID + ': ' + message )
        else:
            logging.warning( 'client' + clientID + ': ' + message )



if __name__ == "__main__":
    port_num = 7788
    try:
        temp = TcpServer('', port_num, 5)
        temp.listen()
    except KeyboardInterrupt:
        print '^C received, shutting down the web server'
        temp.sock.close()
        exit()
