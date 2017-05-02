package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Round5Red extends Thread {
    
    public static ConcurrentMap<Integer, List<Edge>> input;
    public static ConcurrentMap<Integer, Long> result;
    private static Object lock;

    Round5Red() {
        input = Round5Map.result;
        result = new ConcurrentHashMap<>();
        lock = new Object();
    }
    public void run() {
        while(!input.isEmpty()) {
            Map.Entry<Integer, List<Edge>> entry = null;
            synchronized(lock) {
                if(input.isEmpty()) {
                    break;
                }
                entry = input.entrySet().iterator().next();
                input.remove(entry.getKey());
            }
            AdjListGraph g = new AdjListGraph();
            for(Edge edge : entry.getValue()) {
                g.addEdge(Integer.toString(edge.node1), Integer.toString(edge.node2));
            //    System.out.println(Integer.toString(edge.node1) + " " +
            //            Integer.toString(edge.node2));
            }
            long cliques = g.countCliquesOfSize(9);
            result.put(entry.getKey(), cliques);
        }
    }
}
