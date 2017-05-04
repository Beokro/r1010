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
    private long CAP = 40000000L;
    private double fpp = 0.000000001;
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
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    @Override
    public synchronized void refresh(int currentSize) throws RemoteException{
        if(this.currentSize >= currentSize) {
            return;
        }
        this.currentSize = currentSize;
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    @Override
    public int getCurrentSize() throws RemoteException{
        return currentSize;
    }

    @Override
    public synchronized void addHistory(int[][] toAdd) throws RemoteException{
        if(elements >= CAP) {
            bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
            elements = 0;
        }
        boolean result = bloomFilter.put(toAdd);
        if(result) {
            elements += 1;
        }
    }

    @Override
    public boolean inHistory(int[][] graph2d) throws RemoteException {
        return bloomFilter.mightContain(graph2d);
    }
    
    @Override
    public synchronized void setCurrentSize(int currentSize) throws RemoteException {
        this.currentSize = currentSize;
    }

    public static void main(String[] args) {

        try {
            RemoteBloomFilterImpl filter = new RemoteBloomFilterImpl();
            RemoteBloomFilter stub = (RemoteBloomFilter) UnicastRemoteObject.exportObject(filter, 0);
            Registry registry = LocateRegistry.createRegistry(RemoteBloomFilter.PORT);
            //Registry registry = LocateRegistry.getRegistry("98.185.210.172", RemoteBloomFilter.PORT);
            registry.bind(RemoteBloomFilter.SERVICE_NAME, stub);
            System.out.println("Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
