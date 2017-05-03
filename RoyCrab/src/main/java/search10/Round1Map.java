package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class Round1Map extends Thread {
    
    public static ConcurrentMap<Integer, Edge> graph;
    public static ConcurrentMap<Integer, Edge> save;
    public static ConcurrentMap<Integer, BlockingQueue<Integer>> result;
    private static Object lock = new Object();

    Round1Map() {
        result = new ConcurrentHashMap<Integer, BlockingQueue<Integer>>();
        save = new ConcurrentHashMap<Integer, Edge>();
    }

    public void run() {
        while(!graph.isEmpty()) {
            Map.Entry<Integer, Edge> entry = null;
            synchronized(lock) {
                if(graph.isEmpty()) {
                    break;
                }
                entry = graph.entrySet().iterator().next();
                graph.remove(entry.getKey());
            }
            Edge edge = entry.getValue();
            save.put(entry.getKey(), entry.getValue());
            int node1 = edge.node1;
            int node2 = edge.node2;
            result.putIfAbsent(node1, new LinkedBlockingQueue<Integer>());
            result.putIfAbsent(node2, new LinkedBlockingQueue<Integer>());
            result.get(node1).add(node2);
            result.get(node2).add(node1);
            
        }
    }
}
