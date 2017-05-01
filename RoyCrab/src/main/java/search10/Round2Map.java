package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class Round2Map extends Thread {
    
    public static BlockingQueue<OneNeighbor> input;
    public static ConcurrentMap<Integer, List<NodeDeg>> result;
    private static Object firstlock;
    private static Object secondlock;

    Round2Map() {
        input = Round1Red.result;
        result = new ConcurrentHashMap<>();
        firstlock = new Object();
        secondlock = new Object();
    }

    public void run() {
        while(!input.isEmpty()) {
            OneNeighbor one = null;
            one = input .poll();
            if(one == null) {
                break;
            }
            NodeDeg node = one.node;
            int neighbor = one.neighbor;
            synchronized(secondlock) {
                if(!result.containsKey(neighbor)) {
                    result.put(neighbor, new ArrayList<NodeDeg>());
                }
                result.get(neighbor).add(node);
            }
        }
    }
}
