#include "TcpClient.h"

void TcpClient::handleReconnect() {
  if ( !connectToHost() ) {
    // connect to original host failed, try backup
    destHost = backupAddr;
    destPort = backupPort;
    connectToHost();
  }
  handleStartup();
}

void TcpClient::tcpSend( vector< string > messages ) {
  string message = "";
  int sendStatus = 0;
  for ( string m : messages ) {
    message += m + "\n";
  }
  sendStatus = send( sock, message.c_str(), message.size(), MSG_NOSIGNAL );
  if ( sendStatus < 0 ) {
    throw "connection failed";
  }

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
  int recvLen = 0;
  char * buffer = new char[ len + 1];
  vector< string > response;
  if ( len > 1448 ) {
    string temp = "";
    recvLen = recv( sock, buffer, len, 0 );
    buffer[ recvLen ] = 0;
    temp += string( buffer );
    while( temp.back() != '\n' ) {
      memset( buffer, '\0', sizeof(char) * ( len + 1 ) );
      recvLen = recv( sock, buffer, len, 0 );
      buffer[ recvLen ] = 0;
      temp += string( buffer );
    }
    response = split( temp , '\n' );
  } else {
    recvLen = recv( sock, buffer, len, 0 );
    response = split( string( buffer ) , '\n' );
    delete[] buffer;
    return response;
  }
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
  return status == 0;
}

bool TcpClient::handleStartup() {
  vector< string > message = { clientClaimMessage };
  vector< string > response;

  tcpSend( message );
  response = tcpRecv( 80 + 800 * 800 );
  backupAddr = response[ 0 ];
  backupPort = stoi( response[ 1 ] );
  currentSize = stoi( response[ 2 ] );
  cliqueSize = stol( response[ 3 ] );
  currentGraph = response[ 4 ];

  // printf( "backup addr = %s, backup port = %d \nsize = %d, clique = %ld \ngraph = %s\n",
  //        backupAddr.c_str(), backupPort, currentSize, cliqueSize, currentGraph.c_str() );
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
    cliqueSize = stol( response[ 1 ] );
    currentGraph = response[ 2 ];
    cout << "server has better graph: " << endl;
  }
}


void TcpClient::handleProblemSizeChanged( vector< string > response ) {
  currentSize = stoi( response[ 1 ] );
  cliqueSize = stol( response[ 2 ] );
  currentGraph = response[ 3 ];
  cout << "problem size now changed to " << currentSize << endl;
  checkMessage( tranmissionCompleteMessage, response[ 4 ] );
}

int** TcpClient::getGraph() {
  int ** graph = new int*[ currentSize ];
  int index = 0;
  for ( int i = 0; i < currentSize; i++ ) {
    graph[ i ] = new int[ currentSize ];
  }
  for ( int i = 0; i < currentSize; i++ ) {
    for ( int j = 0; j < currentSize; j++ ) {
      graph[ i ][ j ] = currentGraph[ index ] - 48;
      index++;
    }
  }
  return graph;
}

double TcpClient::getAlpha() {
  vector< string > message = { alphaRequest };
  vector< string > response;
  tcpSend( message );
  response = tcpRecv( 20 );
  return stod( response[ 0 ] );
}

void TcpClient::setCurrentGraph( int** graph ) {
  string temp( currentSize * currentSize, '0' );
  int index = 0;
  for ( int i = 0; i < currentSize; i++ ) {
    for ( int j = 0; j < currentSize; j++ ) {
      if ( graph[ i ][ j ] == 1 ) {
        temp[ index ] = '1';
      }
      index++;
    }
  }
  currentGraph = temp;
}

void TcpClient::updateFromAlg( int problemSize, long cliqueSize, int** graph ) {
  this->currentSize = problemSize;
  this->cliqueSize = cliqueSize;
  setCurrentGraph( graph );
  cout << "start exchange\n";
  try{
    startExchange();
  } catch( ... ) {
    handleReconnect();
  }
}


// handle reconnect, updateFromalg, string to array, array to string

int main() {
  TcpClient test( "127.0.0.1", 7788 );
  int ** graph = new int*[ 305 ];
  int cliqueSize = 1000;
  for ( int i = 0; i < 305; i++ ) {
    graph[ i ] = new int[ 305 ];
    graph[ i ][ 2 ] = 1;
  }
  test.run();

  while ( true ) {
    test.updateFromAlg( 305, cliqueSize, graph );
    cout << test.getAlpha() << endl;
    cliqueSize -= 3;
    sleep( 5 );
  }


  for ( int i = 0; i < 305; i++ ) {
    delete[] graph[ i ];
  }
  delete[] graph;
}
