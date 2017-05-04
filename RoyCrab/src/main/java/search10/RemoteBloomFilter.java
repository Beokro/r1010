/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package search10;
import com.google.common.hash.BloomFilter;
import java.rmi.*;
/**
 *
 * @author Seraph_Roy
 */
public interface RemoteBloomFilter extends Remote {
    public static int PORT = 2014;
    public static String SERVICE_NAME = "RemoteBloomFilter";
    public boolean inHistory(int[][] graph2d) throws RemoteException;
    public void addHistory(BloomFilter<int[][]> toAdd) throws RemoteException;
    public int getCurrentSize() throws RemoteException;
    public void refresh() throws RemoteException;
}
