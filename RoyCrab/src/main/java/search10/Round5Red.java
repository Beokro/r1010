package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.Map;


public class Round5Red extends Thread {
    
    public static ConcurrentMap<Integer, BlockingQueue<Edge>> input;
    public static ConcurrentMap<Integer, Long> result;
    private static Object lock = new Object();

    Round5Red() {
        input = Round5Map.result;
        result = new ConcurrentHashMap<Integer, Long>();
    }
    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Integer, BlockingQueue<Edge>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            MatrixGraph g = new MatrixGraph(Alg.client.getCurrentSize());
            for(Edge edge : entry.getValue()) {
                g.addEdge(edge.node1, edge.node2);
            }
            long cliques = g.count9Cliques();
            result.put(entry.getKey(), cliques);
        }
    }
}
