import sys
import socket
import threading

requestMessage = 'request'
denyMessage = 'deny'
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
            threading.Thread( target = self.handleClient, args = ( client, address ) ).start()

    def handleClient( self, client, address ):
        recvSize = 15

        # Loop Start
        # send my currentSize and graph to the clinet to start the computation
        self.lock.acquire()
        client.send( str( self.currentSize ) )
        client.send( self.currentGraph )
        self.lock.release()

        while True:
            try:
                data = client.recv( recvSize )
                if data:
                    self.handleClique( data, client )
                else:
                    raise error('Client disconnected')
            except:
                client.close()
                return False

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
        client.send( exchangeConfirmedMessage )

        # check if server and client have the same problem size
        clientProblemSize = int( client.recv( 20 ) )
        clientCliqueSize = int ( client.recv( 20 ) )

        self.lock.acquire()

        # case B
        if clientProblemSize != self.currentSize:
            self.handleDifferentProblemSize( client )
        # case A_0
        elif clientCliqueSize == 0:
            self.requestAndHandleNewGraph( client, True )
        # case A_1
        elif clientCliqueSize < self.cliqueSize:
            self.requestAndHandleNewGraph( client, False )
        # case A_2
        else:
            self.denyNewGraph( client )
        self.lock.release()

    def handleUnexpectMessage( self, client, expecting, received ):
        global restartMessage
        print 'received unexpected message'
        print 'expecting ' + expecting
        print 'received ' + received
        client.send( restartMessage )
        return

    def requestAndHandleNewGraph( self, client, iszero ):
        global requestMessage
        global errorMessage
        global tranmissionCompleteMessage
        graph = ' '
        recvSize = self.currentSize * self.currentSize + 10
        # request the matrix from the client
        client.send( requestMessage )
        graph = client.recv( recvSize )

        # case A_0.2 & case A_1.2, invalid graph
        if not self.validGraph( graph ):
            client.send( errorMessage )
            return

        # case A_0.1 && case A_1.1, keep the graph
        self.currentGraph = graph

        # case A_0.1, increment the problem size
        if iszero:
            self.currentSize += 1
            self.currentGraph = ' '
            self.cliqueSize = sys.maxsize
            self.handleDifferentProblemSize( client )

        # case A_0.1 && case A_1.1, tranmission complete
        client.send( tranmissionCompleteMessage )

    def denyNewGraph( self, client ):
        # deny the matrix, not need to send if it is worse than current one
        # instead server will send its graph to client
        global denyMessage
        client.send( denyMessage )
        client.send( self.cliqueSize )
        client.send( self.currentGraph )

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
        client.send( problemSizeChangedMessage )
        client.send( self.currentSize )
        return


if __name__ == "__main__":
    port_num = input("Port? ")
    TcpServer('',port_num, 5).listen()
