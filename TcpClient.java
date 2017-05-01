package search10;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.lang.NullPointerException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.Random;


// look at the example at the main
// bascially call updateFromAlg( int problemSize, int cliqueSize, int[][] graph )
// whenever coummuncation with server is needed
// use getCurrentSize, getCliqueSize, getGraph to check on the update from the server
// if graph does not change, it means server either accept client's graph or
// server has a graph that has the same clique size
// if graph changes, it means either problem size is increased or server sent client
// a graph with smaller clique
// default graph from server is all 0's, in this case clique size is meaningless
// don't call getGraph too often, conversion from intern representation to 2d array
// might take a long time
public class TcpClient {
    static final String requestMessage = "request";
    static final String denyMessage = "deny";
    static final String tieMessage = "tie";
    static final String errorMessage = "error";
    static final String problemSizeChangedMessage = "sizeChanged";
    static final String restartMessage = "restart";
    static final String exchangeConfirmedMessage = "confirmed";
    static final String exchangeStartMessage = "start";
    static final String tranmissionCompleteMessage = "complete";
    static final String readFailedMessage = "readFailed";
    static final String clientClaimMessage = "claimClient";

    private String destHost;
    private int destPort;
    private int currentSize = 0;
    private int cliqueSize = Integer.MAX_VALUE;
    private String currentGraph = " ";
    private String backupAddr = " ";
    private int backupPort = -1;
    private Socket sock;
    private BufferedReader sockReader;
    private BufferedWriter sockWriter;

    public TcpClient( String destHost, int destPort ) {
        this.destHost = destHost;
        this.destPort = destPort;
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public int getCliqueSize() {
        return cliqueSize;
    }

    public int[][] getGraph() {
        return translateGraphToArray( currentGraph );
    }

    public boolean connectToHost() {
        try {
            sock = new Socket( destHost, destPort );
            sockReader = new BufferedReader( new InputStreamReader( sock.getInputStream() ) );
            sockWriter = new BufferedWriter( new OutputStreamWriter( sock.getOutputStream()) );
        } catch ( IOException i ) {
            System.out.println( "failed to connect to host" );
            i.printStackTrace();
            return false;
        }
        return true;
    }

    // start connection to host
    public void run() {
        String message;
        // need to handle the case that connection failed
        connectToHost();
        handleStartUp();
    }

    public void handleStartUp() {
        write( new String[] { clientClaimMessage } );
        backupAddr = read();
        backupPort = Integer.parseInt( read() );
        currentSize = Integer.parseInt( read() );
        cliqueSize = Integer.parseInt( read() );
        currentGraph = read();
        System.out.println( "Client start to work on problem with size " +
                            Integer.toString( currentSize ) + " and clique size " +
                            Integer.toString( cliqueSize ) );

    }

    // call by algorithm, start the exchange with server
    // if graph from update is invalid, graph will be empty
    // the reuslt graph return by getter will be all -1
    public void updateFromAlg( int problemSize, int cliqueSize, int[][] graph ) {
        this.currentSize = problemSize;
        this.cliqueSize = cliqueSize;
        this.currentGraph = translateGraphToString( graph );
        // invalid graph
        if ( currentGraph == " " ) {
            return;
        }
        try {
            startExchange();
        } catch( NullPointerException i ) {
            handleReconnect();
        }
    }

    public String translateGraphToString( int[][] graph ) {
        int size1 = graph.length;
        int size2 = 0;
        StringBuilder message = new StringBuilder();

        if ( size1 == 0 ) {
            return " ";
        }
        size2 = graph[ 0 ].length;
        for ( int i = 0; i < size1 ; i++ ) {
            for ( int j = 0; j < size2; j++ ) {
                message.append( Integer.toString( graph[ i ][ j ] ) );
            }
        }
        return message.toString();
    }

    public int[][] translateGraphToArray( String graph ) {
        int[][] message = new int[ currentSize ][ currentSize ];
        int counter = 0;

        if ( graph.length() != currentSize * currentSize ) {
            return message;
        }

        for ( int i = 0; i < currentSize; i++ ) {
            for ( int j = 0; j < currentSize; j++ ) {
                message[ i ][ j ] = graph.charAt( counter );
                counter++;
            }
        }
        return message;
    }

    // handle the exchange with server
    public void startExchange() {
        String message;

        System.out.println( "exchange start" );
        write( new String[] { exchangeStartMessage } );
        message = read();
        if ( !message.equals( exchangeConfirmedMessage ) ) {
            System.out.println( "message = " + message );
            System.out.println( "expecting = " + exchangeConfirmedMessage );
            // exchange is not sync with server, end conversion
            return;
        }
        backupAddr = read();
        backupPort = Integer.parseInt( read() );
        write( new String[] { Integer.toString( currentSize ),
                              Integer.toString( cliqueSize )} );
        message = read();
        if ( message.equals( requestMessage ) ) {
            // case A_0 and case A_1
            handleRequestGraph();
        } else if ( message.equals( denyMessage ) ) {
            // case A_2
            handleDeny();
        } else if ( message.equals( problemSizeChangedMessage ) ) {
            handleProblemSizeChanged();
        } else {
            System.out.println( "Unexpected message from server " + message );
        }
    }

    public void handleRequestGraph() {
        String message = "";
        System.out.println( "server request client side graph" );
        write( new String[] { currentGraph } );
        message = read();

        if ( message.equals( errorMessage ) ) {
            // case A_0.2, case A_1.2
            System.out.println( "client side graph is corrupted or invalid" );
            return;
        } else if ( message.equals( problemSizeChangedMessage ) ) {
            // case A_0.1
            handleProblemSizeChanged();
        } else if ( message.equals( tranmissionCompleteMessage ) ) {
            // case A_1.1
            System.out.println( "exchange complete" );
        } else {
            unpextedMessage( tranmissionCompleteMessage, message );
        }

    }

    public void handleProblemSizeChanged() {
        String message = "";
        this.currentSize = Integer.parseInt( read() );
        this.cliqueSize = Integer.parseInt( read() );
        this.currentGraph = read();
        System.out.println( "problem size not matched with server, now = " + this.currentSize );
        message = read();
        if ( message.equals( tranmissionCompleteMessage ) ) {
            System.out.println( "exchange complete" );
        } else {
            unpextedMessage( tranmissionCompleteMessage, message );
        }
    }

    public void unpextedMessage( String expecting, String got ) {
        System.out.println( "expecting " + expecting );
        System.out.println( "got " + got );
    }

    public void handleDeny() {
        String message = read();
        if ( message.equals( tieMessage ) ) {
            System.out.println( "server and client haave same clique" );
        } else {
            this.cliqueSize = Integer.parseInt( message );
            this.currentGraph = read();
            System.out.println( "server has better clique " + message );
        }
    }

    public void handleReconnect() {
        close();
        // if reconnect to main server failed, connect to backup server instead
        if ( !connectToHost() ) {
            destHost = backupAddr;
            destPort = backupPort;
            connectToHost();
        }
        handleStartUp();
    }

    // read from client
    public String read() {
        try {
            return sockReader.readLine();
        } catch( IOException i ) {
            System.out.println( "failed to read from host" );
            i.printStackTrace();
            return readFailedMessage;
        }
    }

    // combine all the messages and send it all at once
    public void write( String[] messages ) {
        try {
            StringBuilder message = new StringBuilder();
            for ( String m : messages ) {
                message.append( m );
                message.append( "\n" );
            }
            sockWriter.write( message.toString() );
            sockWriter.flush();
        } catch( IOException i ) {
            System.out.println( "failed to send message to host" );
            i.printStackTrace();
            return;
        }
    }

    // close the connection gracefully
    public void close() {
        try {
            sock.close();
        } catch( IOException i ) {
            System.out.println( "failed to close the connection with server" );
            i.printStackTrace();
            return;
        }
    }


    public static void main( String[] args ) {
        TcpClient client = new TcpClient( "localhost", 7788 );
        Random rand = new Random();
        int reduce = 0;
        int currentClique = 500;
        int [][] graph = new int[ 5 ][ 5 ];
        client.run();
        /*
          regualr test
        client.updateFromAlg( 6, 5, "0000000000000000000000000" );
        client.updateFromAlg( 5, 5, "0000000000000000000000000" );
        client.updateFromAlg( 5, 5, "0000000000000000000000000" );
        client.updateFromAlg( 5, 4, "000000000000000000000000" );
        client.updateFromAlg( 5, 4, "0000000000000000000000000" );
        client.updateFromAlg( 5, 0, "0000000000000000000000000" );
        client.updateFromAlg( 6, 100, "000000000000000000000000000000000000" );
        */
        client.updateFromAlg( 5, currentClique, graph );

        while ( true ) {
            try{
                TimeUnit.SECONDS.sleep( 5 );
            } catch ( Exception e ) {
                return;
            }
            reduce = rand.nextInt( 5 );
            currentClique -= reduce;
            if ( currentClique <= 0 ) {
                break;
            }
            client.updateFromAlg( 5, currentClique, graph );
        }

        client.close();
    }

}
