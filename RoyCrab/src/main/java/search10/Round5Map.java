package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;


public class Round5Map extends Thread {
    
    public static ConcurrentMap<Edge, Set<Integer>> input;
    public static ConcurrentMap<Integer, List<Edge>> result;
    private static Object firstlock;
    private static Object secondlock;

    Round5Map() {
        input = Round4Red.result;
        result = new ConcurrentHashMap<>();
        firstlock = new Object();
        secondlock = new Object();
    }
    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Edge, Set<Integer>> entry = null;
            synchronized(firstlock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            for(int node : entry.getValue()) {
                synchronized(secondlock) {
                    if(!result.containsKey(node)) {
                        result.put(node, new ArrayList<Edge>());
                    }
                    result.get(node).add(entry.getKey());
                }
            }
        }
    }
}
