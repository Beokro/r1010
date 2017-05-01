#include <iostream>
#include <stdio.h>
#include <string.h>
#include <string>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <iterator>
#include <sstream>
#include <vector>
#include <climits>


using namespace std;

class TcpClient {
private:
  const string requestMessage = "request";
  const string denyMessage = "deny";
  const string tieMessage = "tie";
  const string errorMessage = "error";
  const string problemSizeChangedMessage = "sizeChanged";
  const string restartMessage = "restart";
  const string exchangeConfirmedMessage = "confirmed";
  const string exchangeStartMessage = "start";
  const string tranmissionCompleteMessage = "complete";
  const string readFailedMessage = "readFailed";
  const string clientClaimMessage = "claimClient";
  string destHost;
  int destPort;
  int currentSize = 0;
  int cliqueSize = INT_MAX;
  string currentGraph = " ";
  string backupAddr = " ";
  int backupPort = -1;
  int sock;

  void tcpSend( vector< string > messages );
  vector< string > tcpRecv( int len );
  bool connectToHost();
  bool handleStartup();
  void startExchange();
  bool checkMessage( string expected, string received );
  void handleRequestGraph();
  void handleDeny( vector< string > response );
  void handleProblemSizeChanged( vector< string > response );

public:
  TcpClient( string destHost, int destPort );
  void run();
  int getCurrentSize() { return currentSize; }
  int getCliqueSize() { return cliqueSize; }
  vector< vector< int > > getGraph(); // todo
  void updateFromAlg( int problemSize, int cliqueSize, vector< vector< int > > graph ); // todo

};

TcpClient::TcpClient( string destHost, int destPort ) {
  this->destHost = destHost;
  this->destPort = destPort;
}
