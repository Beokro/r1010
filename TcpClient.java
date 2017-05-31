import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.lang.NullPointerException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.concurrent.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

/*
class Edge implements java.io.Serializable {
    int node1;
    int node2;
    Edge(int node1, int node2) {
        this.node1 = node1;
        this.node2 = node2;
    }
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof Edge)) {
            return false;
        }
        Edge temp = (Edge)o;
        return node1 == temp.node1 && node2 == temp.node2;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + this.node1;
        hash = 79 * hash + this.node2;
        return hash;
    }
}
*/

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
    static final String alphaRequest = "alphaReq";
    static final String cliRequest = "cliReq";

    private String destHost;
    private int destPort;
    private int currentSize = 0;
    private long cliqueSize = Long.MAX_VALUE;
    private ConcurrentHashMap<Edge, AtomicLong> currentMap;
    private boolean validMap = false;
    private String currentGraph = " ";
    private String backupAddr = " ";
    private int backupPort = -1;
    private Socket sock;
    private BufferedReader sockReader;
    private BufferedWriter sockWriter;

    public TcpClient( String destHost, int destPort ) {
        this.destHost = destHost;
        this.destPort = destPort;
        run();
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public long getCliqueSize() {
        return cliqueSize;
    }

    public int[][] getGraph() {
        return translateGraphToArray( currentGraph );
    }

    public boolean getValidMap() {
        return validMap;
    }

    public ConcurrentHashMap<Edge, AtomicLong> getMap() {
        return currentMap;
    }

    public double getAlpha() {
        String message;
        write( new String[] { alphaRequest } );
        message = read();
        return Double.parseDouble( message );
    }

    public  long[] getServerCli() {
        long serverCurrentSize;
        long serverCliSize;
        write( new String[] { cliRequest } );
        serverCurrentSize = Long.parseLong( read() );
        serverCliSize = Long.parseLong( read() );
        return new long[] { serverCurrentSize, serverCliSize };
    }

    String mapToString( ConcurrentHashMap<Edge, AtomicLong> map ) {
        StringBuilder temp = new StringBuilder("");
        for ( Map.Entry<Edge, AtomicLong> entry : map.entrySet() ) {
            temp.append( Integer.toString( entry.getKey().node1 ) + "_" );
            temp.append( Integer.toString( entry.getKey().node2 ) + "_" );
            temp.append( Long.toString( entry.getValue().get() ) + "@" );
        }
        return temp.toString();
    }

    ConcurrentHashMap<Edge, AtomicLong> stringToMap( String s ) {
        ConcurrentHashMap<Edge, AtomicLong> map = new ConcurrentHashMap<Edge, AtomicLong>();
        String[] entries = s.split( "@" );
        String[] nums;
        Edge e;
        AtomicLong al;

        for ( String entry : entries ) {
            nums = entry.split( "_" );
            e = new Edge( Integer.parseInt( nums[ 0 ] ), Integer.parseInt( nums[ 1 ] ) );
            al = new AtomicLong( Long.parseLong( nums[ 2 ] ) );
            map.put( e, al );
        }
        return map;
    }

    private void readMap() {
        String val = read();
        if ( val.equals( "1" ) ) {
            System.out.println( "get 1 for read\n" );
            validMap = true;
            this.currentMap = stringToMap( read() );
        } else {
            System.out.println( "get 0 for read\n" );
            validMap = false;
            // empty, uselessMap
            read();
            this.currentMap = null;
            return;
        }
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
        cliqueSize = Long.parseLong( read() );
        currentGraph = read();
        readMap();
        System.out.println( "Client start to work on problem with size " +
                            Integer.toString( currentSize ) + " and clique size " +
                            Long.toString( cliqueSize ) );

    }


    // call by algorithm, start the exchange with server
    // if graph from update is invalid, graph will be empty
    // the reuslt graph return by getter will be all -1
    public void updateFromAlg( int problemSize, long cliqueSize, int[][] graph,
                               ConcurrentHashMap<Edge, AtomicLong> map) {
        this.currentSize = problemSize;
        this.cliqueSize = cliqueSize;
        this.currentGraph = translateGraphToString( graph );
        this.currentMap = map;
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
                message[ i ][ j ] = graph.charAt( counter ) - 48;
                counter++;
            }
        }
        return message;
    }

    // handle the exchange with server
    public void startExchange() {
        String message;
        // set the map to be valid, if exception caught, set it to false
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
                              Long.toString( cliqueSize )} );
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

    private static Object fromString( String s ) throws IOException ,
                                                        ClassNotFoundException {
        byte [] data = Base64.getDecoder().decode( s );
        ObjectInputStream ois = new ObjectInputStream( new ByteArrayInputStream(  data ) );
        Object o  = ois.readObject();
        ois.close();
        return o;
    }

    /** Write the object to a Base64 string. */
    private static String toString( Serializable o ) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( o );
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray()); 
    }

    public void handleRequestGraph() {
        String message = "";
        System.out.println( "server request client side graph" );

        write( new String[] { currentGraph, mapToString( this.currentMap ) } );
        message = read();
        // map does not change, therefore valid
        validMap = true;

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
        this.cliqueSize = Long.parseLong( read() );
        this.currentGraph = read();
        readMap();
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
            System.out.println( "server and client have same clique" );
        } else {
            this.cliqueSize = Long.parseLong( message );
            this.currentGraph = read();
            readMap();
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
        /*
        TcpClient client = new TcpClient( "127.0.0.1", 7788 );
        Random rand = new Random();
        int reduce = 0;
        int currentClique = 600;
        int [][] graph = new int[ 306 ][ 306 ];
        ConcurrentHashMap<Edge, AtomicLong> map = new ConcurrentHashMap<Edge, AtomicLong>();
        Edge e = new Edge( 1, 2 );
        AtomicLong l = new AtomicLong( 3 );
        map.put( e, l );

        long[] temp = client.getServerCli();
        System.out.println( temp[ 0 ] );
        System.out.println( temp[ 1 ] );
        if ( client.getValidMap() ) {
            System.out.println( "map is setted up" );
        } else {
            System.out.println( "not set up" );
        }


        client.updateFromAlg( 306, currentClique, graph, map );

        temp = client.getServerCli();
        System.out.println( temp[ 0 ] );
        System.out.println( temp[ 1 ] );

        if ( client.getValidMap() ) {
            System.out.println( "map is setted up" );
        } else {
            System.out.println( "not set up" );
        }

        map = client.getMap();
        System.out.println( map.get( e ) );

        /*
        while ( true ) {
            try{
                TimeUnit.SECONDS.sleep( 2 );
            } catch ( Exception e ) {
                return;
            }
            reduce = rand.nextInt( 5 );
            currentClique -= reduce;
            if ( currentClique <= 0 ) {
                break;
            }
            client.updateFromAlg( 306, currentClique, graph );
            System.out.println( client.getAlpha() );
            }

        client.close();
        */
    }

}
