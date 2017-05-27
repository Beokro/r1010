import sys
import socket
import threading
import os
import logging
import getopt
import time
import random
import textwrap
import os.path
import smtplib
import numpy as np
from random import randint
from email.MIMEMultipart import MIMEMultipart
from email.MIMEText import MIMEText
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
alphaRequest = 'alphaReq'
firstBackup = 'first'
normalBackup = 'normal'
lastAnswerRequest = 'lastAnsReq'
tempFileName = 'tempFile'
answerFileName = 'answer'
newAnswerFileName = 'newAnswer'
backupSyncTime = 300
answerSaveTime = 600

def packBitMap( graph ):
    print 'compressed'
    index = 0
    bitIndex = 0
    length = len( graph ) - 7
    newList = []
    current = 128
    mask = 64
    while index < length:
        for x in range( 0, 7 ):
            if graph[ index + x ] == '1':
                current = current | mask
            mask = mask >> 1
        newList.append( chr( current ) )
        current = 128
        index += 7
        mask = 64
    remain = length + 7 - index
    for x in range( 0, remain ):
        if graph[ index + x ] == '1':
            current = current | mask
        mask = mask >> 1
    newList.append( chr( current ) )

    print 'compress done'
    return ''.join( newList )

def UnpackBitMap( graph, length ):
    temp = 0
    myList = []
    for x in graph:
        temp = ord( x )
        mask = 64
        while mask != 0:
            # print mask
            if temp & mask != 0:
                myList.append( '1' )
            else:
                myList.append( '0' )
            mask = mask >> 1
    ans = ''.join( myList )
    return ans[ :length ]

def generateRandomNumber( n ):
    tempList = []
    for _ in range( 0, n ):
        tempList.append( str( randint( 0, 1 ) ) )
    return ''.join( tempList )

def calc_rms(x, scale):
    shape = (x.shape[0]//scale, scale)
    X = np.lib.stride_tricks.as_strided(x,shape=shape)
    scale_ax = np.arange(scale)
    rms = np.zeros(X.shape[0])
    for e, xcut in enumerate(X):
        coeff = np.polyfit(scale_ax, xcut, 1)
        xfit = np.polyval(coeff, scale_ax)
        rms[e] = np.sqrt(np.mean((xcut-xfit)**2))
    return rms

def dfa(data, scale_lim = [5,9], scale_dens = 0.25):
    data = np.frombuffer(data)
    cumSum = np.cumsum(data - np.mean(data))
    scales = (2**np.arange(scale_lim[0], scale_lim[1], scale_dens)).astype(np.int)
    fluct = np.zeros(len(scales))
    for index, item in enumerate(scales):
        fluct[index] = np.mean(np.sqrt(calc_rms(cumSum, item)**2))
    coeff = np.polyfit(np.log2(scales), np.log2(fluct), 1)
    return coeff[0]

def chunks(l, n):
    """Yield successive n-sized chunks from l."""
    for i in range(0, len(l), n):
        yield l[i:i + n]

class TcpServer( object ):
    def __init__( self, host, port, destHost, destPort, timeout, logDir, backup, currentSize, readFromTemp, generate ):
        self.host = host
        self.port = port
        self.destHost = destHost
        self.destPort = destPort
        self.timeout = timeout
        self.logDir = logDir
        self.backup = backup
        self.readFromTemp = readFromTemp
        self.firstCandidateAddr = '127.0.0.1'
        self.firstCandidatePort = -1
        self.myAddr = ' '
        self.myPort = -1
        self.firstCandidate = False
        self.lockChecker = False
        self.lock = threading.Lock()
        self.lastResult = -1
        self.lastGraph = ' '
        self.currentSize = currentSize
        self.alpha = -1
        self.cliqueRecord = []
        self.cliqueRecordCount = 0
        self.isSetup = '0'
        self.mapString = ' '
        if self.backup:
            self.currentGraph = ' '
        else:
            self.currentGraph = self.generateGraph()
        self.cliqueSize = sys.maxsize
        self.counter = 0
        self.lockID = '-1'
        if generate:
            self.recordAnswer()
            self.lastResult = currentSize
            self.currentSize += 1
            self.currentGraph = self.defaultGraph()
            self.cleanLogFile()
            print 'new problem size = ' + str( self.currentSize )
            self.doLogging( 'answer found, update problem size', '0', True )
        self.sock = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
        self.sock.setsockopt( socket.SOL_SOCKET, socket.SO_REUSEADDR, 1 )
        self.sock.bind( ( self.host, self.port ) )
        logging.basicConfig( format = '%(asctime)s %(levelname)s: %(message)s',
                             filename = self.logDir,
                             level = logging.DEBUG )
        self.doLogging( 'server run on address: ' + host + ' port: ' + str( port ), '-1' )

    def generateGraph( self ):
        global newAnswerFileName

        # set the lastResult, lastGraph. return new current graph
        if not os.path.isfile( newAnswerFileName ):
            self.createNewAnswer()

        with open( newAnswerFileName ) as f:
            content = f.readlines()

        content = [ x.strip() for x in content ]
        listSize = len( content )
        print listSize
        self.lastResult = int( content[ listSize - 4 ] )
        self.lastGraph = content[ listSize - 3 ]

        if self.readFromTemp:
            return self.getTempGraph()

        # server knows the currentSize
        if self.currentSize != -1:
            target = self.currentSize
        else:
            self.currentSize = target = self.lastResult + 1

        if self.lastResult > target - 1:
            print 'random generate targe = ' + str( target )
            print 'do not start client until generation is done'
            return self.defaultGraph( rand = True )
        else:
            self.currentGraph = content[ 1 ]
            print 'use answer, generate graph for ' + str( self.currentSize )
            print 'do not start client until generation is done'
            return self.defaultGraph( useLastGraphAsBase = True )

    def createNewAnswer( self ):
        global answerFileName
        global newAnswerFileName

        with open ( answerFileName ) as f:
            content = f.readlines()
        content = [ x.strip() for x in content ]
        listSize = len( content )
        lastResult =  int( content[ listSize - 4 ] )
        lastGraph = content[ listSize - 3 ]
        with open( newAnswerFileName, "w+" ) as myfile:
            myfile.write( str( lastResult ) + '\n' )
            myfile.write( lastGraph + '\n\n\n' )

    def getTempGraph( self ):
        with open ( tempFileName ) as f:
            content = f.readlines()
        self.currentSize = int( content[ 0 ] )
        print 'current size is ' + str( self.currentSize )
        return content[ 1 ].rstrip('\n')

    def updateLastResult( self, client, startUp = False ):
        if startUp:
            self.sendPacket( client, [ str( self.currentSize ),
                                       str( self.cliqueSize ),
                                       str( self.currentGraph ),
                                       str( self.lastResult ),
                                       str( self.lastGraph ) ] )
        else:
            self.sendPacket( client, [ str( self.lastResult ),
                                       str( self.lastGraph ) ] )

    def defaultGraph( self, rand = False, useLastGraphAsBase = False ):
        if rand:
            num = self.currentSize * self.currentSize
            index = 0
            res = [ '' ]
            while index < num:
                res.append( str( randint( 0, 1 ) ) )
                index += 1
            print 'generation complete'
            return ''.join( res )
        else:
            res = ''
            index = 0
            appendNum = self.currentSize - self.lastResult
            print 'start wrap'
            if useLastGraphAsBase:
                glists = list( chunks( self.lastGraph ,  self.lastResult ) )
                print len( glists )
            else:
                glists = list( chunks( self.currentGraph ,  self.currentSize - 1 ) )
            print 'end wrap'
            for g in glists:
                res += g + generateRandomNumber( appendNum )
            res += generateRandomNumber( self.currentSize )
            print 'generation complete'
            return res

    def appendCliqueRecord( self, clique ):
        self.cliqueRecord.append( clique )
        self.cliqueRecordCount += 1
        if self.cliqueRecordCount == 1000:
            self.doLogging( 'update alpha ', ' ', isServer = True )
            try:
                self.alpha = dfa( self.cliqueRecord )
            except:
                self.cliqueRecord = []
                self.cliqueRecordCount = 0
            print 'new alpha = ' + str( self.alpha )
            self.cliqueRecord = []
            self.cliqueRecordCount = 0

    def listen( self ):
        self.sock.listen( 200 )

        ttt = threading.Thread( target = self.saveTempAnswer )
        ttt.daemon = True
        ttt.start()

        ttt = threading.Thread( target = self.deadLockBreaker )
        ttt.daemon = True
        ttt.start()

        if self.backup:
            tt = threading.Thread( target = self.contactMainServer )
            tt.daemon = True
            tt.start()

        while True:
            client, address = self.sock.accept()
            # need to be more careful about the timeout
            # client.settimeout( 60 )
            self.counter += 1
            t = threading.Thread( target = self.handleClient,
                                  args = ( client, address, str( self.counter ) ) )
            t.daemon = True
            t.start()

    def deadLockBreaker( self ):
        while True:
            if self.lockChecker:
                # lock being lock too long, might be a dead lock
                self.doLogging( 'deadlock breaker force to release a lock with ID ' + str( self.lockID ), ' ', isServer = True )
                try:
                    self.releaseLock()
                except:
                    print 'deadlock breaker caused exception'
            if self.lockID != '-1':
                self.lockChecker = True
            time.sleep( 30 )

    def saveTempAnswer( self ):
        global answerSaveTime
        global tempFileName
        if self.backup:
            time.sleep( answerSaveTime )
        while True:
            try:
                self.getLock( '-2' )
                with open( tempFileName, "w+" ) as myfile:
                    self.doLogging( 'saving temp answer to the file, current clique = ' + str( self.cliqueSize ), ' ', isServer = True )
                    myfile.write( str( self.currentSize ) + '\n' )
                    myfile.write( self.currentGraph + '\n\n\n' )
                self.releaseLock()
                time.sleep( answerSaveTime )
            except:
                if self.lockID == '-2':
                    self.releaseLock()


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
            Server -> lastResult
            Server -> lastGraph
            Backup -> sync complete
        '''
        self.sendPacket( backupSock, [ syncRequestMessage ] )
        data = self.recvPacket( backupSock, 60 + 850 * 850 * 2 )
        self.getLock( '-3' )

        self.currentSize = int( data[ 0 ] )
        self.cliqueSize = int( data[ 1 ] )
        self.currentGraph = data[ 2 ]
        self.recordAnswer( data[ 3 ], data[ 4 ] )

        self.releaseLock()
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
        global lastAnswerRequest
        while True:
            time.sleep( backupSyncTime )
            self.doLogging( 'periodic sync start', ' ', isServer = True )
            global syncRequestMessage
            global syncCompleteMessage

            self.sendPacket( backupSock, [ syncRequestMessage ] )
            data = self.recvPacket( backupSock, 80 )
            self.getLock( '-3' )

            currentSize = int( data[ 0 ] )
            cliqueSize = int( data[ 1 ] )
            self.firstCandidateAddr = data[ 2 ]
            self.firstCandidatePort = int( data[ 3 ] )

            if self.currentSize != currentSize or self.cliqueSize > cliqueSize:
                self.currentSize = currentSize
                self.cliqueSize = cliqueSize
                self.sendPacket( backupSock, [ requestMessage ] )
                data = self.recvPacket( backupSock, self.currentSize * self.currentSize + 10 )
                self.currentGraph = data[ 0 ]
                if self.currentSize != currentSize:
                    self.sendPacket( backupSock, [ lastAnswerRequest ] )
                    data = self.recvPacket( backupSock,
                                            self.currentSize * self.currentSize + 30 )

                    self.recordAnswer( data[ 0 ], data[ 1 ] )

            self.sendPacket( backupSock, [ syncCompleteMessage ] )
            self.doLogging( 'currentSize = ' + str( self.currentSize ) +\
                            ' cliqueSize =' + str( self.cliqueSize ),
                            ' ', isServer = True )

            self.releaseLock()
            self.doLogging( 'periodic sync complete', ' ', isServer = True )

    def handleMainServerDown( self ):
        try:
            # try to reconnect to server
            if self.lockID == '-3':
                self.releaseLock()
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
        global serverClaimMessage
        global clientClaimMessage
        self.doLogging( 'new connection establish', clientID )
        # send my currentSize and graph to the clinet to start the computation
        self.getLock( clientID )
        data = self.recvPacket( client, 50 )
        if data[ 0 ] == clientClaimMessage:
            print 'it is a client'
            self.handleClientToServer( client, address, clientID )
        elif data[ 0 ] == serverClaimMessage:
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
        global lastAnswerRequest
        global firstBackup
        global normalBackup
        self.doLogging( ' is a server', clientID )
        self.releaseLock()
        '''
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

        self.getLock( clientID )
        self.updateLastResult( client, startUp = True )
        self.releaseLock()

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
                    raise error( 'backup server disconnected' )
            except Exception as e:
                if self.lockID == clientID:
                    self.releaseLock()
                exc_type, exc_obj, exc_tb = sys.exc_info()
                fname = os.path.split( exc_tb.tb_frame.f_code.co_filename ) [ 1 ]
                print ( exc_type, fname, exc_tb.tb_lineno )
                client.close()
                return


    def handleBackupSync( self, data, client, clientID ):
        global syncRequestMessage
        global syncCompleteMessage
        global requestMessage
        global lastAnswerRequest
        '''
            Backup -> sync request
            Server -> current size
            Server -> clique size
            Server -> first candidate address
        '''
        if data != syncRequestMessage:
            self.handleUnexpectMessage( client, syncRequestMessage, data, clientID, True )
            return
        self.getLock( clientID )
        self.sendPacket( client, [ str( self.currentSize ),
                                   str( self.cliqueSize ),
                                   self.firstCandidateAddr,
                                   str( self.firstCandidatePort ) ] )
        self.releaseLock()
        data = self.recvPacket( client, 20 )[ 0 ]

        '''
            Backup -> graph request
            Server -> current graph
            Backup -> sync complete
        '''
        if data == requestMessage:
            self.getLock( clientID )
            self.sendPacket( client, [ self.currentGraph ] )
            self.releaseLock()
            data = self.recvPacket( client, 20 )[ 0 ]

        '''
            Backup -> last answer request
            Server -> last size
            Server -> last graph
            Backup -> sync complete
        '''
        if data == lastAnswerRequest:
            self.getLock( clientID )
            self.updateLastResult( client )
            self.releaseLock()
            data = self.recvPacket( client, 20 )[ 0 ]

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
                                   self.currentGraph,
                                   self.isSetup,
                                   self.mapString ] )
        self.releaseLock()
        while True:
            try:
                data = self.recvPacket( client, 20 )[ 0 ]
                if data:
                    self.doLogging( 'data exchange start', clientID )
                    self.handleClique( data, client, clientID )
                else:
                    self.doLogging(  'client disconnected', clientID, 'warning' )
                    raise Exception( 'Client disconnected' )
            except Exception as e:
                if self.lockID == clientID:
                    self.releaseLock()
                exc_type, exc_obj, exc_tb = sys.exc_info()
                fname = os.path.split( exc_tb.tb_frame.f_code.co_filename ) [ 1 ]
                print ( exc_type, fname, exc_tb.tb_lineno )
                client.close()
                return


    def handleClique( self, data, client, clientID ):
        global exchangeStartMessage
        global exchangeConfirmedMessage
        global alphaRequest
        # matrix received is at most size * size big, give some extra jic
        recvSize = 0
        clientProblemSize = -1
        clientCliqueSize = -1
        message = ''

        if data == alphaRequest:
            # just respond with the aphs
            self.sendPacket( client, [ self.alpha ] )
            return

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

        self.getLock( clientID )
        self.appendCliqueRecord( clientCliqueSize )
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
        self.releaseLock()

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
        recvSize = self.currentSize * self.currentSize * 2 + 50

        # request the matrix from the client
        self.doLogging( 'request graph from client', clientID )
        self.sendPacket( client, [ requestMessage ] )

        msg = self.recvPacket( client, recvSize )
        graph = msg[ 0 ]

        # case A_0.2 & case A_1.2, invalid graph
        if not self.validGraph( graph, clientID ):
            self.doLogging( 'graph from client is invalid', clientID, 'warning' )
            self.sendPacket( client, [ errorMessage ] )
            return

        # case A_0.1 && case A_1.1, keep the graph
        self.currentGraph = graph
        self.mapString = msg[ 1 ]
        self.isSetup = '1'
        self.cliqueSize = clientCliqueSize

        # case A_0.1, increment the problem size, include tranmission complete message
        if clientCliqueSize == 0:
            self.recordAnswer()
            # self.sendAnswerWithEmail()
            print self.currentSize
            print self.currentGraph
            self.currentSize += 1
            self.currentGraph = self.defaultGraph()
            self.isSetup = '0'
            self.mapString = ' '
            self.cliqueSize = sys.maxsize
            self.doLogging( 'answer found, update problem size', clientID )
            self.cleanLogFile()
            self.handleDifferentProblemSize( client, clientID )
        # case A_1.1, tranmission complete
        else:
            self.doLogging( 'exchange complete', clientID )
            self.sendPacket( client, [ tranmissionCompleteMessage ] )

    def recordAnswer( self, size = ' ', graph = ' '):
        global answerFileName
        print 'record size = ' + size
        if size == ' ':
            size = str( self.currentSize )
        if graph == ' ':
            graph = self.currentGraph
        self.lastResult = self.currentSize
        self.lastGraph = graph

        with open( answerFileName, "a+" ) as myfile:
            myfile.write( size + '\n' )
            myfile.write( graph + '\n\n\n' )
        with open( newAnswerFileName, "w+" ) as myfile:
            myfile.write( size + '\n' )
            myfile.write( graph + '\n\n\n' )

    def sendAnswerWithEmail( self ):
        print 'start sending with email'
        msg = MIMEMultipart()
        msg['From'] = 'beokro@gmail.com'
        msg['To'] = 'xujiacao@gmail.com'
        msg['Subject'] = 'new answer found'
        message = str( self.currentSize ) + '\n' + self.currentGraph
        msg.attach(MIMEText(message))

        mailserver = smtplib.SMTP('smtp.gmail.com',587)
        # identify ourselves to smtp gmail client
        mailserver.ehlo()
        # secure our email with tls encryption
        mailserver.starttls()
        # re-identify ourselves as an encrypted connection
        mailserver.ehlo()
        mailserver.login('beokro@gmail.com', 'q503748283')

        mailserver.sendmail('beokro@gmail.com','xujiacao@gmail.com',msg.as_string())

        mailserver.quit()
        print 'done with sending email'

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
            self.sendPacket( client, [ denyMessage,
                                       str( self.cliqueSize ),
                                       str( self.currentGraph ),
                                       self.isSetup,
                                       self.mapString ] )

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
        self.doLogging( 'problem sized not matched, give it a random graph to start with',
                        clientID, 'warning' )
        datas = [ problemSizeChangedMessage,
                  str( self.currentSize ),
                  str( self.cliqueSize ),
                  self.defaultGraph( useLastGraphAsBase = True ),
                  self.isSetup,
                  self.mapString,
                  tranmissionCompleteMessage ]
        self.sendPacket( client, datas )
        return

    # end of handle client to server


    # take care of sending multiple data at once
    def sendPacket( self, client, datas ):
        message = ''
        for data in datas:
            message += str( data ) + '\n'
        client.send( message )

    # take care of receiving and split the data inside packet
    def recvPacket( self, client, size ):
        if size > 1448:
            data = ''
            temp = client.recv( size )
            while not temp.endswith( '\n' ):
                data += temp
                temp = client.recv( size )
            data += temp
        else:
            data = client.recv( size )
        return data.split( '\n' )

    def cleanLogFile( self ):
        # log file can hold logs for at most 10 problems
        if self.currentSize % 2 == 0:
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

    def getLock( self, ID ):
        self.lock.acquire()
        self.lockID = ID

    def releaseLock( self ):
        self.lockID = '-1'
        self.lockChecker = False
        self.lock.release()

def usage():
    print 'python server.py [-h] [-p portnumber] [-t timeout] [-l logDir] [-a destAddr] [-d destPort] [-b] [-r]'


if __name__ == "__main__":
    try:
        opts, args = getopt.getopt( sys.argv[ 1: ], "p:ht:l:a:d:bc:ng",
                                    [ "port=", "help", "timeout=", "log=",
                                      "addrdest=", "destport", "backup",
                                      "currentSize=", "notreadFromTemp",
                                      "generate" ] )
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
    currentSize = -1
    readFromTemp = True
    generate = False

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
        elif o in ( "-n", "--notreadFromTemp" ):
            readFromTemp = False
        elif o in ( "-g", "--generate" ):
            generate = True
        else:
            assert False, "unhandled option"

    if backup and logDir == 'server.log':
        logDir = 'backup.log'

    try:
        temp = TcpServer( '', port, destIP, destPort, timeout, logDir, backup, currentSize, readFromTemp, generate )
        temp.listen()
    except KeyboardInterrupt:
        print '^C received, shutting down the web server'
        temp.sock.close()
        exit()
