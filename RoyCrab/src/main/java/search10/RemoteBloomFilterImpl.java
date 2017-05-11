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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

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

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
            RemoteBloomFilterImpl.log.info(timeStamp + " Save Done");

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
    public static final Logger log = Logger.getLogger("RemoteBloomFilter");
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
        fpp = 0.0000000001;
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
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        log.info(timeStamp + " Refresh");
    }

    @Override
    public int getCurrentSize() throws RemoteException{
        return currentSize;
    }

    @Override
    public synchronized void addHistory(int[][] toAdd) throws RemoteException {
        if((elements % (CAP / 100000) == 0 && elements != 0) || elements >= CAP) {
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
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
            log.info(timeStamp + " Start Backup");
            backupThread = new BackupThread(this, address);
            backupThread.start();

        }
        
        boolean result = bloomFilter.put(toAdd);
        if(result) {
            elements += 1;
        }
        log.info("Elements: " + elements);
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
            RemoteBloomFilter filter = getOrNull(RemoteBloomFilterImpl.address);
            RemoteBloomFilterImpl.log.setLevel(Level.INFO);
            if(filter == null) {
                filter = new RemoteBloomFilterImpl();
            }
            
            RemoteBloomFilter stub = (RemoteBloomFilter) UnicastRemoteObject.exportObject(filter, RemoteBloomFilter.PORT);
            Registry registry = LocateRegistry.createRegistry(RemoteBloomFilter.PORT);
            registry.bind(RemoteBloomFilter.SERVICE_NAME, stub);
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            log.info(timeStamp + " Remote bloom filter starts");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
