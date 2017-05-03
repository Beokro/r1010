package search10;
import com.google.common.hash.*;

public class History {
    private BloomFilter<int[][]> bloomFilter;    
    int currentSize;
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

    public void addHistory(BloomFilter<int[][]> toAdd) {
        bloomFilter.putAll(toAdd);
        elements += 1;
        if(elements > CAP) {
            bloomFilter = BloomFilter.create(graphFunnel, CAP, fpp);
            elements = 0;
        }
    }

    public boolean inHistory(int[][] graph2d) {
        return bloomFilter.mightContain(graph2d);
    }

}
