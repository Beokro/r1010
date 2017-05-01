package search10;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;

class NodeDeg {
    int node;
    int degree;
    NodeDeg(int node, int degree) {
        this.node = node;
        this.degree = degree;
    }
    public boolean equals(NodeDeg input) {
        return node == input.node && degree == input.degree;
    }
}

class Edge {
    int node1;
    int node2;
    Edge(int node1, int node2) {
        this.node1 = node1;
        this.node2 = node2;
    }
    public boolean equals(Edge e) {
        return node1 == e.node1 && node2 == e.node2;
    }
}

public class Alg {
    public static TcpClient client;
    public static BlockingQueue<Edge> graph;
    
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

    }
}
