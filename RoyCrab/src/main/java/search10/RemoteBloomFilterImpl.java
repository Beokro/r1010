package search10;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import com.google.common.hash.*;
import java.rmi.Naming; 
import java.rmi.RemoteException; 
import java.rmi.server.UnicastRemoteObject;

public class RemoteBloomFilterImpl implements RemoteBloomFilter {
    private BloomFilter<int[][]> bloomFilter;    
    private int currentSize;
    private TcpClient tcpClient;
    private long CAP = 4000000L;
    private double fpp = 0.00000001;
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

    RemoteBloomFilterImpl() throws RemoteException{
        tcpClient = new TcpClient("98.185.210.172", 7788);
        tcpClient.run();
        currentSize = tcpClient.getCurrentSize();
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    public synchronized void refresh() throws RemoteException{
        tcpClient = new TcpClient("98.185.210.172", 7788);
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

        try {
            RemoteBloomFilterImpl filter = new RemoteBloomFilterImpl();
            RemoteBloomFilter stub = (RemoteBloomFilter) UnicastRemoteObject.exportObject(filter, 0);
            Registry registry = LocateRegistry.createRegistry(RemoteBloomFilter.PORT);
            registry.bind(RemoteBloomFilter.SERVICE_NAME, stub);
            //Registry registry = LocateRegistry.createRegistry(PORT);
            //Registry registry = LocateRegistry.getRegistry();
            //registry.rebind(SERVICE_NAME, filter);
            System.out.println("Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
