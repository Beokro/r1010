import sys
import socket
import threading
import os

requestMessage = 'request'
denyMessage = 'deny'
tieMessage = 'tie'
errorMessage = 'error'
problemSizeChangedMessage = 'sizeChanged'
restartMessage = 'restart'
exchangeConfirmedMessage = 'confirmed'
exchangeStartMessage = 'start'
tranmissionCompleteMessage = 'complete'



class TcpServer( object ):
    def __init__( self, host, port, currentSize ):
        self.host = host
        self.port = port
        self.lock = threading.Lock()
        self.currentSize = currentSize
        self.currentGraph = ' '
        self.cliqueSize = sys.maxsize
        self.sock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
        self.sock.setsockopt( socket.SOL_SOCKET, socket.SO_REUSEADDR, 1 )
        self.sock.bind( ( self.host, self.port ) )

    def listen( self ):
        self.sock.listen( 5 )
        while True:
            client, address = self.sock.accept()
            # need to be more careful about the timeout
            # client.settimeout( 60 )
            t = threading.Thread( target = self.handleClient, args = ( client, address ) )
            t.daemon = True
            t.start()

    def handleClient( self, client, address ):
        recvSize = 15

        # Loop Start
        print 'new connection establish'
        # send my currentSize and graph to the clinet to start the computation
        self.lock.acquire()
        self.sendPacket( client, [ str( self.currentSize ), self.currentGraph ] )
        self.lock.release()

        while True:
            try:
                data = self.recvPacket( client, recvSize )[ 0 ]
                print 'data exchange start'
                if data:
                    self.handleClique( data, client )
                else:
                    raise error('Client disconnected')
            except Exception as e:
                exc_type, exc_obj, exc_tb = sys.exc_info()
                fname = os.path.split(exc_tb.tb_frame.f_code.co_filename)[1]
                print(exc_type, fname, exc_tb.tb_lineno)
                client.close()
                return

    def handleClique( self, data, client ):
        global exchangeStartMessage
        global exchangeConfirmedMessage
        # matrix received is at most size * size big, give some extra jic
        recvSize = 0
        clientProblemSize = -1
        clientCliqueSize = -1
        message = ''

        # make sure the data server receiviing is what it is expecting
        if data != exchangeStartMessage:
            self.handleUnexpectMessage( client, exchangeStartMessage, data )
            return
        self.sendPacket( client, [ exchangeConfirmedMessage ] )

        # check if server and client have the same problem size
        datas = self.recvPacket( client, 45 )
        clientProblemSize = int( datas[ 0 ] )
        clientCliqueSize = int ( datas[ 1 ] )
        print 'client has problem size: ' + str( clientProblemSize ) +\
            ' clique: ' + str( clientCliqueSize )

        self.lock.acquire()

        # case B
        if clientProblemSize != self.currentSize:
            self.handleDifferentProblemSize( client )
        # case A_1, A_0
        elif clientCliqueSize < self.cliqueSize:
            self.requestAndHandleNewGraph( client, clientCliqueSize )
        # case A_2
        elif clientCliqueSize > self.cliqueSize:
            self.denyNewGraph( client, False )
        # case A_3
        else:
            self.denyNewGraph( client, True )
        self.lock.release()

    def handleUnexpectMessage( self, client, expecting, received ):
        global restartMessage
        print 'received unexpected message'
        print 'expecting ' + expecting
        print 'received ' + received
        self.sendPacket( client, [ restartMessage ] )
        return

    def requestAndHandleNewGraph( self, client, clientCliqueSize ):
        global requestMessage
        global errorMessage
        global tranmissionCompleteMessage
        graph = ' '
        recvSize = self.currentSize * self.currentSize + 10

        # request the matrix from the client
        print 'request graph from client'
        self.sendPacket( client, [ requestMessage ] )
        graph = self.recvPacket( client, recvSize )[ 0 ]

        # case A_0.2 & case A_1.2, invalid graph
        if not self.validGraph( graph ):
            print 'graph from client is invalid'
            self.sendPacket( client, [ errorMessage ] )
            return

        # case A_0.1 && case A_1.1, keep the graph
        self.currentGraph = graph
        self.cliqueSize = clientCliqueSize

        # case A_0.1, increment the problem size, include tranmission complete message
        if clientCliqueSize == 0:
            self.currentSize += 1
            self.currentGraph = ' '
            self.cliqueSize = sys.maxsize
            print 'answer found, update problem size'
            self.handleDifferentProblemSize( client )
        # case A_1.1, tranmission complete
        else:
            print 'exchange complete'
            self.sendPacket( client, [ tranmissionCompleteMessage ] )

    def denyNewGraph( self, client, tie ):
        # deny the matrix, not need to send if it is worse than current one
        # instead server will send its graph to client
        global denyMessage
        global tieMessage
        if tie:
            print 'server and client has the same clique number'
            self.sendPacket( client, [ denyMessage, tieMessage ] )
        else :
            print 'server has better graph, send it to client'
            self.sendPacket( client, [ denyMessage, str( self.cliqueSize ), str( self.currentGraph ) ] )

    def validGraph( self, graph ):
        # don't need to lock here, if size has been changed
        # no need to process the request from client anyway
        if len( graph ) != self.currentSize * self.currentSize:
            print 'graph received from client has wrong length'
            print graph
            return False
        for g in graph:
            if g != '0' and g != '1':
                print 'graph contained invalid character' + g
                print graph
                return False
        return True

    # handle case B
    def handleDifferentProblemSize( self, client ):
        global problemSizeChangedMessage
        print 'problem sized not matched, start to sync'
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


if __name__ == "__main__":
    port_num = 7788
    try:
        temp = TcpServer('', port_num, 5)
        temp.listen()
    except KeyboardInterrupt:
        print '^C received, shutting down the web server'
        temp.sock.close()
        exit()
