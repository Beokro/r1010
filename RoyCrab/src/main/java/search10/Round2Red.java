package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.Map;


public class Round2Red extends Thread {
    
    public static ConcurrentMap<Integer, List<NodeDeg>> input = Round2Map.result;
    public static ConcurrentMap<NodeDeg, NodeDeg> result;
    public static ConcurrentMap<NodeDeg, NodeDeg> save;
    private static Object lock;

    Round2Red() {
        input = Round2Map.result;
        result = new ConcurrentHashMap<>();
        save = new ConcurrentHashMap<>();
        lock = new Object();
    }

    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Integer, List<NodeDeg>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            int node = entry.getKey();
            List<NodeDeg> neighbors = entry.getValue();
            int degree = neighbors.size();
            for(NodeDeg neighbor : neighbors) {
                if(Alg.doubleCheck(node, degree, neighbor.node, neighbor.degree)) {
                    //System.out.println(Integer.toString(node) + " " + 
                    //        Integer.toString(degree) + " " +
                    //        Integer.toString(neighbor.node) + " " +
                    //        Integer.toString(neighbor.degree));
                    result.put(new NodeDeg(node, degree), neighbor);
                    save.put(new NodeDeg(node, degree), neighbor);
                }
            }
        }
    }
}
