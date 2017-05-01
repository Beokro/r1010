package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class Round3Map extends Thread {
    
    public static ConcurrentMap<NodeDeg, NodeDeg> input;
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
            Map.Entry<NodeDeg, NodeDeg> entry = null;
            synchronized(firstlock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            NodeDeg node = entry.getKey();
            NodeDeg neighbor = entry.getValue();
            synchronized(secondlock) {
                if(!result.containsKey(node)) {
                    result.put(node, new ArrayList<NodeDeg>());
                }
                result.get(node).add(neighbor);
            }
        }
    }
}
