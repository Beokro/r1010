#include <fstream>
#include <sstream>
#include <iostream>
using namespace std;
double** getnCr() {
  double** nCr = new double*[ 1001 ];
    std::stringstream iss(line);
  for ( int i = 0; i < 1001; i++ ) {
    nCr[ i ] = new double[ 101 ];
  }
  for ( int i = 0 ; i< 1001; i++ ) {
    for ( int j = 0; j < 101; j++ ) {
      string val = "";
      std::getline(iss, val, ',');
      if ( !iss.good() )
        break;
      std::stringstream convertor(val);
      convertor >> nCr[i][j];
    }
  }
  return nCr;
}

int main() {
  double** temp = getnCr();
  cout << temp[1000][100];
  delete[] temp;
  return 0;
}