package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;
import java.util.Map;

public class Round1Red extends Thread {
    
    public static ConcurrentMap<Integer, List<Integer>> input;
    public static BlockingQueue<OneNeighbor> result;
    public static Object lock;

    Round1Red() {
        lock = new Object();
        input = Round1Map.result;
        result = new LinkedBlockingQueue<>();
    }

    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Integer, List<Integer>> entry = null;

            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            int nodeNum = entry.getKey();
            List<Integer> neighbors = entry.getValue();
            NodeDeg node = new NodeDeg(nodeNum, neighbors.size());
            for(int neighbor : neighbors) {
                result.add(new OneNeighbor(node, neighbor));
            }
        }
    }
}