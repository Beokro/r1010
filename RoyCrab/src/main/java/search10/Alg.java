package search10;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class NodeDeg {
    int node;
    int degree;
    NodeDeg(int node, int degree) {
        this.node = node;
        this.degree = degree;
    }
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof NodeDeg)) {
            return false;
        }
        NodeDeg temp = (NodeDeg)o;
        boolean haha = node == temp.node && degree == temp.degree;
        return haha;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.node;
        hash = 37 * hash + this.degree;
        return hash;
    }
}

class Edge {
    int node1;
    int node2;
    Edge(int node1, int node2) {
        this.node1 = node1;
        this.node2 = node2;
    }
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof Edge)) {
            return false;
        }
        Edge temp = (Edge)o;
        return node1 == temp.node1 && node2 == temp.node2;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + this.node1;
        hash = 79 * hash + this.node2;
        return hash;
    }
}

class OneNeighbor {
    NodeDeg node;
    int neighbor;
    OneNeighbor (NodeDeg node, int neighbor) {
        this.node = node;
        this.neighbor = neighbor;
    }
}

class NodeDegPair {
    NodeDeg node1;
    NodeDeg node2;
    NodeDegPair(NodeDeg node1, NodeDeg node2) {
        this.node1 = node1;
        this.node2 = node2;
    } 
    
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof NodeDegPair)) {
            return false;
        }
        NodeDegPair temp = (NodeDegPair)o;
        return node1.equals(temp.node1) && node2.equals(temp.node2);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.node1);
        hash = 67 * hash + Objects.hashCode(this.node2);
        return hash;
    }
}

public class Alg {
    public static TcpClient client;
    public static Deque<Edge> graph = new ArrayDeque<>();
    public static int[][] graph2d;

    Alg(String destHost, int destPort) {
        client = new TcpClient(destHost, destPort);
    }

    private static void createGraph() {
        int size = client.getCurrentSize();
        Round1Map.graph = new LinkedBlockingQueue<>();
        size = 11;
        for(int i = 0; i < size; i++) {
            for(int j = i + 1; j < size; j++) {
                if(graph2d[i][j] == 1) {
                    graph.add(new Edge(i, j));
                    Round1Map.graph.offer(new Edge(i, j));
                } else {
                    graph.add(new Edge(i + size, j + size));
                    Round1Map.graph.offer(new Edge(i + size, j + size));
                }
            }
        }
    }

    public static boolean doubleCheck(int node1, int degree1, int node2, int degree2) {
        if(degree1 < degree2) {
            return true;
        }
        if(degree1 == degree2 && node1 < node2) {
            return true;
        }
        return false;
    }

    private void runRound(int round, int cores) {
        Deque<Thread> threads = new ArrayDeque<Thread>();
        for(int i = 0; i < cores; i++) {
            MapRed thisRound = RoundFactory.makeRound(round);
            threads.addFirst(thisRound.map);
            threads.addLast(thisRound.reduce);
        }
        for(Thread thread : threads) {
            thread.start();
        }
        for(Thread thread : threads) {
            try {
                thread.join();
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private int countCliques() {
        int cores = Runtime.getRuntime().availableProcessors();

        // something to setup Round1Map.graph
        runRound(1, cores); 
        runRound(2, cores); 
        runRound(3, cores); 
        runRound(4, cores); 
        runRound(5, cores); 
        int cliques = 0;
        for(int i : Round5Red.result.values()) {
            if(cliques < cliques + i) {
                return Integer.MAX_VALUE;
            } else {
                cliques += i;
            }
        }
        return cliques;
    }
    public static void main( String[] args ) {
        //if(args.length < 2) {
        //    System.err.println("Usage: java -jar <jar executable> <destHost> <destPort>");
        //    return;
        //}
        //String destHost = args[0];
        //int destPort = Integer.parseInt(args[1]);
        String destHost = "haha";
        int destPort = 10;
        Alg haha = new Alg(destHost, destPort);
        Alg.graph2d = new int[11][];
        for(int i = 0; i < 11; i++) {
            Alg.graph2d[i] = new int[11];
        }
        for(int i = 0; i < 11; i++) {
            for(int j = 0; j < 11; j++) {
                Alg.graph2d[i][j] = 1;
            }
        }
        Alg.createGraph();
        System.out.println(haha.countCliques());
    }
}
