package search10;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic;
import com.google.common.hash;

public class RemoteBloomFilter extends UnicastRemoteObject {
    private BloomFilter<int[][]> bloomFilter;    
    private int currentSize;
    private TcpClient tcpClient;
    private Object lock = new Object();

    RemoteBloomFilter() {
        tcpClient = new TcpClient("98.185.210.172", 7777);
        tcpClient.run();
        Funnel<int[][]> graphFunnel = new Funnel<int[][]>() {
            @Override
            public void funnel(int[][] graph2d, PrimitiveSink into) {
                for(int i = 0; i < currentSize; i++) {
                    for(int j = i + 1; j < currentSize; j++) {
                        into.putInt(graph2d[i][j]);
                    }
                }
            }
        };
        currentSize = tcpClient.getCurrentSize();
        bloomFilter = create(graphFunnel, 
                                    currentSize * (currentSize + 1) / 2, 0.00001);
    }

    public void refresh() {
        Funnel<int[][]> graphFunnel = new Funnel<int[][]>() {
            @Override
            public void funnel(int[][] graph2d, PrimitiveSink into) {
                for(int i = 0; i < currentSize; i++) {
                    for(int j = i + 1; j < currentSize; j++) {
                        into.putInt(graph2d[i][j]);
                    }
                }
            }
        };
        currentSize = tcpClient.getCurrentSize();
        bloomFilter = create(graphFunnel, 
                                    currentSize * (currentSize + 1) / 2, 0.00001);
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public static void main(String[] args) {
        if(System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }
        try {
            RemoteBloomFilter filter = new RemoteBloomFilter();
            Registry registry = LocateRegistry.createRegistry("7789");
            registry.rebind("RemoteBloomFilter", filter);
            System.out.println("Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
