package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class Round1Map extends Thread {
    
    public static ConcurrentMap<Integer, Edge> graph;
    public static ConcurrentMap<Integer, Edge> save = new ConcurrentHashMap<>();
    public static ConcurrentMap<Integer, List<Integer>> result;
    private static Object firstlock;
    private static Object secondlock;

    Round1Map() {
        result = new ConcurrentHashMap<>();
        firstlock = new Object();
        secondlock = new Object();
    }

    public void run() {
        while(!graph.isEmpty()) {
            Map.Entry<Integer, Edge> entry = null;
            synchronized(firstlock) {
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
            synchronized(secondlock) {
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
