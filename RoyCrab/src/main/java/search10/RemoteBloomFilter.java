/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package search10;
import java.io.Serializable;
import java.rmi.*;
/**
 *
 * @author Seraph_Roy
 */
public interface RemoteBloomFilter extends Remote, Serializable {
    public static int PORT = 9010;
    public static String SERVICE_NAME = "RemoteBloomFilter";
    public boolean inHistory(int[][] graph2d) throws RemoteException;
    public void addHistory(int[][] toAdd) throws RemoteException;
    public int getCurrentSize() throws RemoteException;
    public void refresh(int currentSize) throws RemoteException;
    public void setCurrentSize(int currentSize) throws RemoteException;
}