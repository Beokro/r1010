package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class Round2Map extends Thread {
    
    public static ConcurrentMap<NodeDeg, Integer> input;
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
            Map.Entry<NodeDeg, Integer> entry = null;
            synchronized(firstlock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            NodeDeg node = entry.getKey();
            int degree = entry.getValue();
            synchronized(secondlock) {
                if(!result.containsKey(degree)) {
                    result.put(degree, new ArrayList<NodeDeg>());
                }
                result.get(degree).add(node);
            }
        }
    }
}
