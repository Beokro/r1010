package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Round4Red extends Thread {
    
    public static ConcurrentMap<Edge, Set<Integer>> input;
    public static ConcurrentMap<Edge, Set<Integer>> result;
    private static Object lock;

    Round4Red() {
        input = Round4Map.result;
        result = new ConcurrentHashMap<Edge, Set<Integer>>();
        lock = new Object();
    }
    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Edge, Set<Integer>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            if(entry.getValue().contains(-1)) {
                entry.getValue().remove(-1);
                result.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
