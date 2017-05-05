package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;


public class Round3Red extends Thread {
    
    public static ConcurrentMap<NodeDeg, List<NodeDeg>> input;
    public static ConcurrentMap<NodeDeg, List<NodeDeg>> result;
    private static Object lock = new Object();

    Round3Red() {
        input = Round3Map.result;
        result = new ConcurrentHashMap<NodeDeg, List<NodeDeg>>();
    }
    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<NodeDeg, List<NodeDeg>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            NodeDeg node = entry.getKey();
            List<NodeDeg> neighbors = entry.getValue();
            int degree = neighbors.size();
            if(degree >= 9) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
