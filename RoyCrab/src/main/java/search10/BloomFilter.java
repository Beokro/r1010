package search10;
import com.google.common.hash.*;

public class BloomFilter {
    com.google.common.hash.BloomFilter<int[][]> bloomFilter; 
    com.google.common.hash.BloomFilter<int[][]> backup;
    private int currentSize;
    private long CAP;
    private double fpp;
    private long elements = 0;
    private Funnel<int[][]> graphFunnel;

    BloomFilter() {
        
        CAP = 4000000L;
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
        bloomFilter = com.google.common.hash.BloomFilter.create(graphFunnel, CAP, fpp);
        elements = 0;
    }
    
    public synchronized void refresh(int currentSize) {
        if(this.currentSize >= currentSize) {
            return;
        }
        this.currentSize = currentSize;
        bloomFilter = com.google.common.hash.BloomFilter.create(graphFunnel, CAP, fpp);
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public synchronized void addHistory(int[][] toAdd) {
        if(elements >= CAP) {
            backup = com.google.common.hash.BloomFilter.create(graphFunnel, CAP, fpp);
            backup.putAll(bloomFilter);
            bloomFilter = com.google.common.hash.BloomFilter.create(graphFunnel, CAP, fpp);
            elements = 0;
        }
        boolean result = bloomFilter.put(toAdd);
        if(result) {
            elements += 1;
        }
    }

    public boolean inHistory(int[][] graph2d) {
        if(backup == null) {
            return bloomFilter.mightContain(graph2d);
        }
        return backup.mightContain(graph2d) || bloomFilter.mightContain(graph2d);
    }

    public synchronized void setCurrentSize(int currentSize) {
        this.currentSize = currentSize;
    }
}