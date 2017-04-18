import sys
import socket
import threading
import fcntl
import time

requestMessage = 'request'
denyMessage = 'deny'
errorMessage = 'error'
problemSizeChangedMessage = 'sizeChanged'
restartMessage = 'restart'
exchangeConfirmedMessage = 'confirmed'
exchangeStartMessage = 'start'
tranmissionCompleteMessage = 'complete'


class TcpClient():
    def __init__( self, destHost, destPort ):
        self.destHost = destHost
        self.destPort = destPort
        self.lock = threading.Lock()
        self.currentSize = 0
        self.currentGraph = ' '
        self.cliqueSize = sys.maxsize
        self.sock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )


    def getResult( self ):
        # read the compute result from the file and then update it
        x = open( 'result')
        while True:
            try:
                fcntl.flock( x, fcntl.LOCK_EX | fcntl.LOCK_NB )
                size = int( x.readline() )
                result = int( x.readline() )
                graph = x.readline()
                fcntl.flock( x, fcntl.LOCK_UN )
                break
            except IOError as e:
                print 'write failed\n'
                # raise on unrelated IOErrors
                if e.errno != errno.EAGAIN:
                    raise
                else:
                    time.sleep(0.1)
        x.close()
        return size, result, graph

    def writeResult( self ):
        # don't want to use w+ flag because that will empty the file without getting lock
        x = open( 'serverUpdate', 'a+' )
        while True:
            try:
                fcntl.flock( x, fcntl.LOCK_EX | fcntl.LOCK_NB )
                # empty the file
                x.seek(0)
                x.truncate()
                x.write( str( self.currentSize ) + '\n' )
                x.write( str( self.cliqueSize ) + '\n' )
                x.write( self.currentGraph )
                fcntl.flock( x, fcntl.LOCK_UN )
                break
            except IOError as e:
                print 'write failed\n'
                # raise on unrelated IOErrors
                if e.errno != errno.EAGAIN:
                    raise
                else:
                    time.sleep(0.1)
        x.close()

    def connctToHost( self ):
        try:
            self.sock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
            self.sock.connect( ( self.destHost, self.destPort ) )
        except:
            print 'connection failed'
            sys.exit()

    def runForEver( self ):
        self.connctToHost()
        result = ' '

        # receive inital state, graph received might be empty
        datas = self.recvPacket( self.sock, self.currentSize * self.currentSize + 30 )
        self.currentSize = int( datas[ 0 ] )
        self.currentGraph = datas[ 1 ]

        while True:
            size, result, graph = self.getResult()
            if result < self.cliqueSize:
                self.currentSize = size
                self.cliqueSize = result
                self.currentGraph = graph
                self.startExchange()
            print result
            time.sleep( 5 )

    def startExchange( self ):
        global exchangeStartMessage
        global exchangeConfirmedMessage
        global requestMessage
        global errorMessage

        server = self.sock
        serverSize = -1
        message = ' '

        # Loop Start
        self.sendPacket( server, exchangeStartMessage )
        message = self.recvPacket( server, 20 )[ 0 ]
        if message != exchangeConfirmedMessage:
            return
        self.sendPacket( server, [ str( self.currentSize ), str( self.cliqueSize ) ] )

        datas = self.recvPacket( server, self.currentSize * self.currentSize + 70 )
        message = datas[ 0 ]
        # case A
        if message == requestMessage:
            self.handleRequestGrpah()
        # case B
        else:
            self.handleProblemSizeChanged( datas )

    def handleRequestGrpah( self ):
        global problemSizeChangedMessage
        global tranmissionCompleteMessage
        global denyMessage
        server = self.sock
        self.sendPacket( server, [ self.currentGraph ] )
        datas = self.recvPacket( server, self.currentSize * self.currentSize + 70 )
        message = datas[ 0 ]

        # case A_0.2, case A_1.2
        if message == errorMessage:
            print 'somehow graph is corrupted'
        # case A_0.1
        elif message == problemSizeChangedMessage:
            self.handleProblemSizeChanged( datas )
            message = datas[ 3 ]
            if message != tranmissionCompleteMessage:
                print "exchange with server end with unexpect " + message
        # case A_1.1
        elif message == tranmissionCompleteMessage:
            return
        # case A_2
        elif message == denyMessage:
            self.cliqueSize = int( datas[ 1 ] )
            self.currentGraph = datas[ 2 ]
        else:
            print "repsone from server is unexpected: " + message
        return

    def handleProblemSizeChanged( self, datas ):
        server = self.sock
        self.currentSize = int( datas[ 1 ] )
        self.cliqueSize = int( datas[ 2 ] )
        self.currentGraph = datas[ 3 ]
        # update the graph to the second file
        self.writeResult()

    # take care of sending multiple data at once
    def sendPacket( self, server, datas ):
        message = ''
        for data in datas:
            message += data + '\n'
        server.send( message )

    # take care of receiving and split the data inside packet
    def recvPacket( self, server, size ):
        data = server.recv( size )
        return data.split( '\n' )



if __name__ == "__main__":
    port_num = 7788
    try:
        temp = TcpClient('127.0.0.1', port_num )
        temp.runForEver()
    except KeyboardInterrupt:
        print '^C received, shutting down the web server'
        temp.sock.close()
        exit()
