#include "TcpClient.h"

// todo
// update, internal tranlate between string and vector
// exceptions

void TcpClient::tcpSend( vector< string > messages ) {
  string message = "";
  for ( string m : messages ) {
    message += m + "\n";
  }
  send( sock, message.c_str(), message.size(), 0 );
}

template<typename Out>
void split(const std::string &s, char delim, Out result) {
  std::stringstream ss;
  ss.str(s);
  std::string item;
  while (std::getline(ss, item, delim)) {
    *(result++) = item;
  }
}

std::vector<std::string> split(const std::string &s, char delim) {
  std::vector<std::string> elems;
  split(s, delim, std::back_inserter(elems));
  return elems;
}

vector< string > TcpClient::tcpRecv( int len ) {
  char * buffer = new char[ len + 1];
  int recvLen = recv( sock, buffer, len, 0 );
  vector< string > response = split( string( buffer ) , '\n' );;
  return response;
}

void TcpClient::run() {
  connectToHost();
  handleStartup();
}

bool TcpClient::connectToHost() {
  int status = 0;
  sock = socket( AF_INET , SOCK_STREAM , 0 );
  if ( sock != -1 ) {
    struct sockaddr_in server;
    server.sin_addr.s_addr = inet_addr( destHost.c_str() );
    server.sin_family = AF_INET;
    server.sin_port = htons( destPort );

    status = connect( sock, ( struct sockaddr * ) &server , sizeof( server ) );
  } else {
    cerr << "scoket creat failed\n";
  }
  return status != 0;
}

bool TcpClient::handleStartup() {
  vector< string > message = { clientClaimMessage };
  vector< string > response;

  tcpSend( message );
  response = tcpRecv( 80 + 800 * 800 );
  backupAddr = response[ 0 ];
  backupPort = stoi( response[ 1 ] );
  currentSize = stoi( response[ 2 ] );
  cliqueSize = stoi( response[ 3 ] );
  currentGraph = response[ 4 ];

  printf( "backup addr = %s, backup port = %d \nsize = %d, clique = %d \ngraph = %s\n",
          backupAddr.c_str(), backupPort, currentSize, cliqueSize, currentGraph.c_str() );
}



void TcpClient::startExchange() {
  vector< string > message1 = { exchangeStartMessage };
  vector< string > message2 = { to_string( currentSize ), to_string( cliqueSize ) };
  vector< string > response;
  int recvSize = 80 + currentSize * currentSize;

  cout << "exchange start\n";
  tcpSend( message1 );
  response = tcpRecv( 60 );
  if ( !checkMessage( exchangeConfirmedMessage, response[ 0 ] ) ) {
    return;
  }
  backupAddr = response[ 1 ];
  backupPort = stoi( response[ 2 ] );

  // send my clique size to server
  tcpSend( message2 );
  response = tcpRecv( recvSize );

  if ( response[ 0 ] == requestMessage ) {
    // case A_0 and case A_1
    handleRequestGraph();
  } else if ( response[ 0 ] == denyMessage ) {
    // case A_2
    handleDeny( response );
  } else if ( response[ 0 ] == problemSizeChangedMessage ) {
    handleProblemSizeChanged( response );
  } else {
    cerr << "after sending my cliquesize to server, received " << response[ 0 ] << endl;
  }
}

bool TcpClient::checkMessage( string expected, string received ) {
  if ( received != expected ) {
    cerr << "expecting: " << expected << "\nreceived: " << received << endl;
    return false;
  }
  return true;
}

void TcpClient::handleRequestGraph() {
  vector< string > message = { currentGraph };
  vector< string > response;
  int recvSize = 80 + currentSize * currentSize;

  cout << "server request client side graph" << endl;
  tcpSend( message );
  response = tcpRecv( recvSize );

  if ( response[ 0 ] == errorMessage ) {
    // case A_0.2, case A_1.2
    cerr << "client side graph is corrupted" << endl;
    return;
  } else if ( response[ 0 ] == problemSizeChangedMessage ) {
    // case A_0.1
    handleProblemSizeChanged( response );
  } else if ( response[ 0 ] == tranmissionCompleteMessage ) {
    // case A_1.1
    cout << "tranmission complete" << endl;
  } else {
    checkMessage( tranmissionCompleteMessage, response[ 0 ] );
  }

}


void TcpClient::handleDeny( vector< string > response ) {
  if ( response[ 1 ] == tieMessage ) {
    cout << "server and client haave same clique" << endl;
  } else {
    cliqueSize = stoi( response[ 1 ] );
    currentGraph = response[ 2 ];
    cout << "server has better graph: " << currentGraph << endl;
  }
}


void TcpClient::handleProblemSizeChanged( vector< string > response ) {
  currentSize = stoi( response[ 1 ] );
  cliqueSize = stoi( response[ 2 ] );
  currentGraph = response[ 3 ];
  cout << "problem size now changed to " << currentSize << endl;
  checkMessage( tranmissionCompleteMessage, response[ 4 ] );
}

int main() {
  TcpClient test( "127.0.0.1", 7788 );
  test.run();
}
