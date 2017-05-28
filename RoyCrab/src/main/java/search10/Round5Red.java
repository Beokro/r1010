package search10;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


public class Round5Red extends Thread {
    
    public static ConcurrentMap<Integer, BlockingQueue<Edge>> input;
    public static ConcurrentMap<Integer, Long> result;
    private static Object lock = new Object();
    Map<Edge, Long> edgeToClique =
                                    new HashMap<Edge, Long>();
    
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
            //MatrixGraph g = new MatrixGraph(Alg.client.getCurrentSize());
            AdjListGraph g = new AdjListGraph();
            g.node = entry.getKey();
            for(Edge edge : entry.getValue()) {
                g.addEdge(Integer.toString(edge.node1), Integer.toString(edge.node2));
                //g.addEdge(edge.node1, edge.node2);
            }
            g.edgeToClique = edgeToClique;
            //long cliques = g.count9Cliques();
            long cliques = g.countCliquesOfSize(9, false);
            result.put(entry.getKey(), cliques);
        }
        for(Map.Entry<Edge, Long> entry : edgeToClique.entrySet()) {
            Alg.edgeToClique.get(entry.getKey()).addAndGet(entry.getValue());
        }
    }
}
