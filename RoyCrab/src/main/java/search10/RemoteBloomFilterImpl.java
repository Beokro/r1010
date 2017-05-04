package search10;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import com.google.common.hash.*;
import java.io.FileInputStream;
import java.rmi.Naming; 
import java.rmi.RemoteException; 
import java.rmi.server.UnicastRemoteObject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

class BackupThread extends Thread {
    BloomFilter<int[][]> backup;
    
    BackupThread( BloomFilter<int[][]> backup) {
        this.backup = backup;
    }
    @Override
    public void run() {
        FileOutputStream fout = null;
        ObjectOutputStream oos = null;
        try {
            fout = new FileOutputStream(RemoteBloomFilterImpl.address);
            oos = new ObjectOutputStream(fout);
            oos.writeObject(backup);

            System.out.println("Backup Done");

            } catch (Exception ex) {

                ex.printStackTrace();

            } finally {

            if (fout != null) {
                try {
                        fout.close();
                } catch (IOException e) {
                        e.printStackTrace();
                }
            }

            if (oos != null) {
                try {
                        oos.close();
                } catch (IOException e) {
                        e.printStackTrace();
                }
            }

        }
    }
}

public class RemoteBloomFilterImpl implements RemoteBloomFilter {
    public static final String address = "./backupBloomFilter";
    BloomFilter<int[][]> bloomFilter; 
    BloomFilter<int[][]> backup;
    private BackupThread backupThread;
    private int currentSize;
    private long CAP;
    private double fpp;
    private long elements = 0;
    private Funnel<int[][]> graphFunnel;

    RemoteBloomFilterImpl() throws RemoteException{
        
        CAP = 40000000L;
        fpp = 0.000000001;
        graphFunnel = new Funnel<int[][]>() {
            @Override
            public void funnel(int[][] graph2d, PrimitiveSink into) {
                for(int i = 0; i < currentSize; i++) {
                    for(int j = i + 1; j < currentSize; j++) {
                        into.putInt(graph2d[i][j]);
                    }
                }
            }
        };
        bloomFilter = backupOrNew(graphFunnel, CAP, fpp);
    }
    
    private static BloomFilter<int[][]> backupOrNew(
                        Funnel<int[][]> graphFunnel, long CAP, double fpp) {
        FileInputStream fin = null;
        ObjectInputStream ois = null;
        BloomFilter<int[][]> result = null;
        try {
            fin = new FileInputStream(address);
            ois = new ObjectInputStream(fin);
            result = (BloomFilter<int[][]>) ois.readObject();
        } catch(IOException e) {
            result = BloomFilter.create(graphFunnel, CAP, fpp);
        } catch (ClassNotFoundException e) {
            result = BloomFilter.create(graphFunnel, CAP, fpp);
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                        e.printStackTrace();
                }
            }

            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                        e.printStackTrace();
                }
            }
        }
        return result;
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
            try {
                backupThread.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(RemoteBloomFilterImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
            backup = BloomFilter.create(graphFunnel, CAP, fpp);
            backup.putAll(bloomFilter);
            backupThread = new BackupThread(this.backup);
            backupThread.start();
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
        if(backup == null) {
            return bloomFilter.mightContain(graph2d);
        }
        return backup.mightContain(graph2d) || bloomFilter.mightContain(graph2d);
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
            registry.bind(RemoteBloomFilter.SERVICE_NAME, stub);
            System.out.println("Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
