package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;


public class Round2Map extends Thread {
    
    public static BlockingQueue<OneNeighbor> input;
    public static ConcurrentMap<Integer, BlockingQueue<NodeDeg>> result;

    Round2Map() {
        input = Round1Red.result;
        result = new ConcurrentHashMap<Integer, BlockingQueue<NodeDeg>>();
    }

    public void run() {
        while(!input.isEmpty()) {
            OneNeighbor one = null;
            one = input.poll();
            if(one == null) {
                break;
            }
            NodeDeg node = one.node;
            int neighbor = one.neighbor;
            result.putIfAbsent(neighbor, new LinkedBlockingQueue<NodeDeg>());
            result.get(neighbor).add(node);
            
        }
    }
}
