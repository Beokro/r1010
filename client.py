import sys
import socket
import threading
import fcntl

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
        return result,graph

    def writeResult( self ):
        # don't want to use w+ flag because that will empty the file without getting lock
        x = open( 'serverUpdate', 'a+' )
        while True:
            try:
                fcntl.flock( x, fcntl.LOCK_EX | fcntl.LOCK_NB )
                # empty the file
                x.seek(0)
                x.truncate()
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
        self.currentSize = int( self.sock.recv( 20 ) )
        self.currentGraph = self.sock.recv( self.currentSize * self.currentSize + 10 )

        while True:
            result, graph = self.getResult()
            if result < self.cliqueSize:
                self.cliqueSize = result
                self.currentGraph = graph
                self.startExchange()

    def startExchange( self ):
        global exchangeStartMessage
        global exchangeConfirmedMessage
        global requestMessage
        global errorMessage

        server = self.sock
        serverSize = -1
        message = ' '

        # Loop Start
        server.send( exchangeStartMessage )
        message = server.recv( 20 )
        if message != exchangeConfirmedMessage:
            return
        server.send( str( self.currentSize ) )
        server.send( str( self.cliqueSize ) )

        message = server.recv( 20 )
        # case A
        if message == requestMessage:
            self.handleRequestGrpah()
        # case B
        else:
            self.handleProblemSizeChanged()

    def handleRequestGrpah( self ):
        global problemSizeChangedMessage
        global tranmissionCompleteMessage
        global denyMessage
        server = self.sock
        server.send( self.currentGraph )
        message = server.recv( 20 )

        # case A_0.2, case A_1.2
        if message == errorMessage:
            print 'somehow graph is corrupted'
        # case A_0.1
        elif message == problemSizeChangedMessage:
            self.handleProblemSizeChanged()
            message = server.recv( 20 )
            if message != tranmissionCompleteMessage:
                print "exchange with server end with unexpect " + message
        # case A_1.1
        elif message == tranmissionCompleteMessage:
            return
        # case A_2
        elif message == denyMessage:
            self.cliqueSize = int( server.recv( 20 ) )
            self.currentGraph = server.recv( self.currentSize * self.currentSize + 10 )
        else:
            print "repsone from server is unexpected: " + message
        return

    def handleProblemSizeChanged( self ):
        server = self.sock
        self.currentSize = int( server.recv( 20 ) )
        self.currentGraph = server.recv( self.currentSize * self.currentSize + 10 )
        # update the graph to the second file
        self.writeResult()


if __name__ == "__main__":
    port_num = 7788
    try:
        temp = TcpClient('127.0.0.1', port_num )
        temp.runForEver()
    except KeyboardInterrupt:
        print '^C received, shutting down the web server'
        temp.sock.close()
        exit()
