package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.ArrayList;

public class Round1Map extends Thread {
    
    public static BlockingQueue<Edge> graph;
    public static BlockingQueue<Edge> save = new BlockingQueue<>();
    public static ConcurrentMap<Integer, List<Integer>> result;
    private static Object lock;

    Round1Map() {
        result = new ConcurrentHashMap<>();
        lock = new Object();
    }

    public void run() {
        while(!graph.isEmpty()) {
            Edge edge = null;
            edge = graph.poll();
            if(edge == null) {
                break;
            }
            save.add(edge);
            int node1 = edge.node1;
            int node2 = edge.node2;
            synchronized(lock) {
                if(!result.containsKey(node1)) {
                    result.put(node1, new ArrayList<Integer>());
                }
                if(!result.containsKey(node2)) {
                    result.put(node2, new ArrayList<Integer>());
                }
                result.get(node1).add(node2);
                result.get(node2).add(node1);
            }
        }
    }
}
