package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;


public class Round5Map extends Thread {
    
    public static ConcurrentMap<Edge, ConcurrentMap<Integer, Integer>> input;
    public static ConcurrentMap<Integer, BlockingQueue<Edge>> result;
    private static Object lock = new Object();

    Round5Map() {
        input = Round4Red.result;
        result = new ConcurrentHashMap<Integer, BlockingQueue<Edge>>();
    }
    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Edge, ConcurrentMap<Integer, Integer>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            for(int node : entry.getValue().keySet()) {
                result.putIfAbsent(node, new LinkedBlockingQueue<Edge>());
                result.get(node).add(entry.getKey());
            }
        }
    }
}
