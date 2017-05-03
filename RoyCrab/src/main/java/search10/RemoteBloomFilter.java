package search10;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import com.google.common.hash.*;

public class RemoteBloomFilter extends UnicastRemoteObject {
    private BloomFilter<int[][]> bloomFilter;    
    private int currentSize;
    private TcpClient tcpClient;
    private long CAP = 4000000L;
    private double fpp = 0.0000001;
    private long elements = 0;
    private Funnel<int[][]> graphFunnel = new Funnel<int[][]>() {
        @Override
        public void funnel(int[][] graph2d, PrimitiveSink into) {
            for(int i = 0; i < currentSize; i++) {
                for(int j = i + 1; j < currentSize; j++) {
                    into.putInt(graph2d[i][j]);
                }
            }
        }
    };

    RemoteBloomFilter() throws RemoteException{
        tcpClient = new TcpClient("98.185.210.172", 7777);
        tcpClient.run();
        currentSize = tcpClient.getCurrentSize();
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    public synchronized void refresh() throws RemoteException{
        tcpClient = new TcpClient("98.185.210.172", 7777);
        tcpClient.run();
        currentSize = tcpClient.getCurrentSize();
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    public int getCurrentSize() throws RemoteException{
        return currentSize;
    }

    public synchronized void addHistory(BloomFilter<int[][]> toAdd) throws RemoteException{
        bloomFilter.putAll(toAdd);
        elements += 1;
        if(elements > CAP) {
            bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
            elements = 0;
        }
    }

    public boolean inHistory(int[][] graph2d) throws RemoteException {
        return bloomFilter.mightContain(graph2d);
    }

    public static void main(String[] args) {
        if(System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }
        try {
            RemoteBloomFilter filter = new RemoteBloomFilter();
            Registry registry = LocateRegistry.createRegistry(7789);
            registry.rebind("RemoteBloomFilter", filter);
            System.out.println("Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
