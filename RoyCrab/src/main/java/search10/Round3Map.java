package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;


public class Round3Map extends Thread {
    
    public static BlockingQueue<NodeDegPair> input;
    public static ConcurrentMap<NodeDeg, List<NodeDeg>> result;
    private static Object lock = new Object();

    Round3Map() {
        input = Round2Red.result;
        result = new ConcurrentHashMap<NodeDeg, List<NodeDeg>>();
    }
    public void run() {
        while(!input.isEmpty()) {
            NodeDegPair one = null;
            one = input.poll();
            if(one == null) {
                break;
            }
            NodeDeg node = one.node1;
            NodeDeg neighbor = one.node2;
            result.putIfAbsent(node, new ArrayList<NodeDeg>());
            synchronized(lock) {
                result.get(node).add(neighbor);
            }
        }
    }
}
