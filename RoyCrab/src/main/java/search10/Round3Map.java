package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class Round3Map extends Thread {
    
    public static BlockingQueue<NodeDegPair> input;
    public static ConcurrentMap<NodeDeg, List<NodeDeg>> result;
    private static Object firstlock;
    private static Object secondlock;

    Round3Map() {
        input = Round2Red.result;
        result = new ConcurrentHashMap<>();
        firstlock = new Object();
        secondlock = new Object();
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
            synchronized(secondlock) {
                if(!result.containsKey(node)) {
                    result.put(node, new ArrayList<NodeDeg>());
                }
                List<NodeDeg> haha = result.get(node);
                result.get(node).add(neighbor);
            }
        }
    }
}
