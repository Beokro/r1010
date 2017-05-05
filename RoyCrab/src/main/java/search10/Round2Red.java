package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;


public class Round2Red extends Thread {
    
    public static ConcurrentMap<Integer, BlockingQueue<NodeDeg>> input;
    public static BlockingQueue<NodeDegPair> result;
    public static BlockingQueue<NodeDegPair> save;
    private static Object lock = new Object();

    Round2Red() {
        input = Round2Map.result;
        result = new LinkedBlockingQueue<NodeDegPair>();
        save = new LinkedBlockingQueue<NodeDegPair>();
    }

    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Integer, BlockingQueue<NodeDeg>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            int node = entry.getKey();
            BlockingQueue<NodeDeg> neighbors = entry.getValue();
            int degree = neighbors.size();
            for(NodeDeg neighbor : neighbors) {
                if(Alg.doubleCheck(node, degree, neighbor.node, neighbor.degree)) {
                    NodeDeg temp = new NodeDeg(node, degree);
                    NodeDegPair insert = new NodeDegPair(temp, neighbor);
                    result.add(insert);
                    save.add(insert);
                }
            }
        }
    }
}
