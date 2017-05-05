package search10;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.Map;


public class Round4Map extends Thread {
    
    public static ConcurrentMap<NodeDeg, List<NodeDeg>> withNeighbors;
    public static BlockingQueue<NodeDegPair> edges;
    public static ConcurrentMap<Edge, ConcurrentMap<Integer, Integer>> result;
    private static Object lock = new Object();

    Round4Map() {
        withNeighbors = Round3Red.result;
        edges = Round2Red.save;
        result = new ConcurrentHashMap<Edge, ConcurrentMap<Integer, Integer>>();
    }
    public void run() {
        while(!edges.isEmpty()) {
            NodeDegPair one = null;
            one = edges.poll();
            if(one == null) {
                break;
            }
            NodeDeg node = one.node1;
            NodeDeg neighbor = one.node2;
            if(Alg.doubleCheck(node.node, node.degree, neighbor.node, neighbor.degree)) {
                Edge edge = new Edge(node.node, neighbor.node);
                result.putIfAbsent(edge, new ConcurrentHashMap<Integer, Integer>());
                result.get(edge).put(-1, 0);
            }
        }
        while(!withNeighbors.isEmpty()) {
            Map.Entry<NodeDeg, List<NodeDeg>> entry = null;
            synchronized(lock) {
                if(withNeighbors.isEmpty()) {
                    break;
                }
                entry = withNeighbors.entrySet().iterator().next();
                withNeighbors.remove(entry.getKey());
            }
            for(int i = 0; i < entry.getValue().size(); i++) {
                for(int j = i + 1; j < entry.getValue().size(); j++) {
                    NodeDeg node1 = entry.getValue().get(i);
                    NodeDeg node2 = entry.getValue().get(j);
                    Edge newEdge = null;
                    if(Alg.doubleCheck(node1.node, 
                                        node1.degree, node2.node, node2.degree)) {
                        newEdge = new Edge(node1.node, node2.node);
                    } else {
                        newEdge = new Edge(node2.node, node1.node);
                    }
                    result.putIfAbsent(newEdge, new ConcurrentHashMap<Integer, Integer>());
                    result.get(newEdge).put(entry.getKey().node, 0);
                    
                }
            }

        }
    }
}
