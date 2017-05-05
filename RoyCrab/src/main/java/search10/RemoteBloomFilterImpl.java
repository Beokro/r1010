package search10;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import com.google.common.hash.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.rmi.RemoteException; 
import java.rmi.server.UnicastRemoteObject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

class BackupThread extends Thread {
    RemoteBloomFilterImpl toSave;
    String address;
    
    BackupThread( RemoteBloomFilterImpl toSave, String address) {
        this.toSave = toSave;
        this.address = address;
    }
    @Override
    public void run() {
        FileOutputStream fout = null;
        ObjectOutputStream oos = null;
        try {
            fout = new FileOutputStream(this.address);
            oos = new ObjectOutputStream(fout);
            RemoteBloomFilterImpl temp = toSave;
            toSave = null;
            oos.writeObject(temp);

            System.out.println("Save Done");

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

public class RemoteBloomFilterImpl implements RemoteBloomFilter, Serializable {
    private static final long serialVersionUID = 233333L;
    public static final String address = "./remoteBloomFilter.backup";
    BloomFilter<int[][]> bloomFilter; 
    BloomFilter<int[][]> backup;
    private static transient BackupThread backupThread = null;
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
                for(int i = 0; i < graph2d.length; i++) {
                    for(int j = i + 1; j < graph2d[i].length; j++) {
                        into.putInt(graph2d[i][j]);
                    }
                }
            }
        };
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
        elements = 0;
    }
    
    private static RemoteBloomFilterImpl getOrNull(String address) {
        FileInputStream f = null;
        ObjectInputStream o = null;
        RemoteBloomFilterImpl result = null;
        try {
            f = new FileInputStream(address);
            o = new ObjectInputStream(f);
            result = (RemoteBloomFilterImpl) o.readObject();
        } catch(FileNotFoundException e) {
            
        } catch(IOException e) {

        } catch (ClassNotFoundException e) {

        } finally {
            if (f != null) {
                try {
                    f.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (o != null) {
                try {
                    o.close();
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
    public synchronized void addHistory(int[][] toAdd) throws RemoteException {
        if((elements % (CAP / 100) == 0 && elements != 0) || elements >= CAP) {
            try {
                if(backupThread != null) {
                    backupThread.join();
                }
            } catch (Exception ex) {
            
            }

            if(elements >= CAP) {

                // move history in memory to backup
                backup = BloomFilter.create(graphFunnel, CAP, fpp);
                backup.putAll(bloomFilter);
                bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
                elements = 0;
            }

            backupThread = new BackupThread(this, address);
            backupThread.start();

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
            RemoteBloomFilterImpl filter = getOrNull(RemoteBloomFilterImpl.address);
            if(filter == null) {
                filter = new RemoteBloomFilterImpl();
            }
            
            RemoteBloomFilter stub = (RemoteBloomFilter) UnicastRemoteObject.exportObject(filter, 0);
            Registry registry = LocateRegistry.createRegistry(RemoteBloomFilter.PORT);
            registry.bind(RemoteBloomFilter.SERVICE_NAME, stub);
            System.out.println("Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
