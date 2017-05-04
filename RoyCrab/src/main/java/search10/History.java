package search10;
import com.google.common.hash.*;

public class History {
    private BloomFilter<int[][]> bloomFilter;    
    int currentSize;
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

    History() {
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    public void refresh(int currentSize) {
        this.currentSize = currentSize;
        bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public void addHistory(int[][] toAdd) {
        if(elements >= CAP) {
            bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
            elements = 0;
        }
        bloomFilter.put(toAdd);
        elements += 1;
    }

    public boolean inHistory(int[][] graph2d) {
        return bloomFilter.mightContain(graph2d);
    }

}
