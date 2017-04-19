import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.Socket;

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

    String destHost;
    int destPort;
    int currentSize = 0;
    int cliqueSize = Integer.MAX_VALUE;
    String currentGraph = " ";
    Socket sock;
    BufferedReader sockReader;
    BufferedWriter sockWriter;

    public TcpClient( String destHost, int destPort ) {
        this.destHost = destHost;
        this.destPort = destPort;
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
        currentSize = Integer.parseInt( read() );
        cliqueSize = Integer.parseInt( read() );
        currentGraph = read();
        System.out.println( "Client start to work on problem with size " +
                            Integer.toString( currentSize ) + " and clique size " +
                            Integer.toString( cliqueSize ) );
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

    public static void main( String[] args ) {
        TcpClient client = new TcpClient( "localhost", 7788 );
        client.run();
    }

}
