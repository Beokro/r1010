import sys
import socket
import threading

requestMessage = 'request'
denyMessage = 'deny'
errorMessage = 'error'


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

        # send my currentSize and graph to the clinet to start the computation
        client.send( str( self.currentSize ) )
        client.send( self.currentGraph )

        while True:
            try:
                # data contains the size of the clique client found with certain graph
                data = client.recv( recvSize )
                if data:
                    self.handleClique( data, client )
                else:
                    raise error('Client disconnected')
            except:
                client.close()
                return False

    def handleClique( self, data, client ):
        # matrix received is at most size * size big, give some extra jic
        recvSize = self.currentSize * self.currentSize + 10
        newCliqueSize = int( data )
        message = ''

        if newCliqueSize < self.cliqueSize:
            # request the matrix from the client
            client.send( requestMessage )
            graph = client.recv( recvSize )
            if not self.validGraph:
                # errored formated graph received from client
                client.send( errorMessage )
                return
            self.currentGraph = graph
        else:
            # deny the matrix, not need to send if it is worse than current one
            # instead server will send its graph to client
            client.send( denyMessage )
            client.send( self.currentGraph )


    def validGraph( self, graph ):
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


if __name__ == "__main__":
    port_num = input("Port? ")
    TcpServer('',port_num, 5).listen()
