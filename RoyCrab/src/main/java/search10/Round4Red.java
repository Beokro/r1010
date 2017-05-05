package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


public class Round4Red extends Thread {
    
    public static ConcurrentMap<Edge, ConcurrentMap<Integer, Integer>> input;
    public static ConcurrentMap<Edge, ConcurrentMap<Integer, Integer>> result;
    private static Object lock = new Object();

    Round4Red() {
        input = Round4Map.result;
        result = new ConcurrentHashMap<Edge, ConcurrentMap<Integer, Integer>>();
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
            if(entry.getValue().containsKey(-1)) {
                entry.getValue().remove(-1);
                result.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
