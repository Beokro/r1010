package search10;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Random;
import java.net.MalformedURLException;
import com.google.common.hash.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


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
        return node == temp.node && degree == temp.degree;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + this.node; hash = 37 * hash + this.degree;
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
    public static TcpClient client = new TcpClient("98.185.210.172", 7788);
    public List<Edge> graph = new ArrayList<Edge>();
    public int[][] graph2d;
    private int currentSize;
    private int change = -1;
    static RemoteBloomFilter history;
    List<Integer> tabuList = new LinkedList<Integer>();
    Set<Integer> tabuSet = new HashSet<Integer>();
    int TABU_CAP;

    Alg() {
        graph = new ArrayList<Edge>();
        tabuList = new LinkedList<Integer>();
        tabuSet = new HashSet<Integer>();
    }

    Edge flip(Edge input) {
        if(input.node1 < currentSize) {
            return new Edge(input.node1 + currentSize, input.node2 + currentSize);
        } else {
            return new Edge(input.node1 - currentSize, input.node2 - currentSize);
        }
    }

    private void clearTabu() {
        tabuList = new LinkedList<Integer>();
        tabuSet = new HashSet<Integer>();
    }
    private void updateTabu(int change) {
        tabuList.add(change);
        tabuSet.add(change);
        if(tabuList.size() > TABU_CAP) {
            int old = tabuList.get(0);
            tabuList.remove(0);
            tabuSet.remove(old);
        }
    }

    private void addHistory(int change) {
        Edge temp = graph.get(change);
        if(temp.node1 >= currentSize) {
            temp = flip(temp);
        }
        graph2d[temp.node1][temp.node2] = Math.abs(graph2d[temp.node1][temp.node2] - 1);
        try {
            history.addHistory(graph2d);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
        graph2d[temp.node1][temp.node2] = Math.abs(graph2d[temp.node1][temp.node2] - 1);
    }

    private void accept() {
        Round1Map.graph.put(this.change, flip(Round1Map.graph.get(this.change))); 
        Edge temp = graph.get(this.change);
        if(temp.node1 >= currentSize) {
            temp = flip(temp);
        }
        graph2d[temp.node1][temp.node2] = Math.abs(graph2d[temp.node1][temp.node2] - 1);
        graph.set(change, temp);
    }

    private boolean hasVisited(int change) {
        if(tabuSet.contains(change)) {
            return true;
        }
        boolean result = false;
        Edge temp = graph.get(change);
        if(temp.node1 >= currentSize) {
            temp = flip(temp);
        }
        graph2d[temp.node1][temp.node2] = Math.abs(graph2d[temp.node1][temp.node2] - 1);
        try {
            result = history.inHistory(graph2d);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
        graph2d[temp.node1][temp.node2] = Math.abs(graph2d[temp.node1][temp.node2] - 1);
        return result;
    }
    private long getRandomNeighbor() {
        Random rand = new Random(System.currentTimeMillis());             
        int change = rand.nextInt(graph.size());
        while(hasVisited(change)) {
            change = rand.nextInt(currentSize);
        }
        this.change = change;
        addHistory(change);
        Round1Map.graph.put(change, flip(Round1Map.graph.get(change)));
        long result = countCliques();
        Round1Map.graph.put(change, flip(Round1Map.graph.get(change)));
        return result;
    }

    private void createGraph() {
        int size = graph2d.length;
        Round1Map.graph = new ConcurrentHashMap<Integer, Edge>();
        int count = 0;
        for(int i = 0; i < size; i++) {
            for(int j = i + 1; j < size; j++) {
                if(graph2d[i][j] == 1) {
                    graph.add(new Edge(i, j));
                    Round1Map.graph.put(count, new Edge(i, j));
                } else {
                    graph.add(new Edge(i + size, j + size));
                    Round1Map.graph.put(count, new Edge(i + size, j + size));
                }
                count += 1;
            }
        }
    }

    public static boolean doubleCheck(int node1, int degree1, int node2, int degree2) {
        if(degree1 < degree2) {
            return true;
        }
        return degree1 == degree2 && node1 < node2;
    }

    private void runRound(int round, int cores) {
        List<Thread> mappers = new ArrayList<Thread>();
        List<Thread> reducers = new ArrayList<Thread>();
        int workers = 2*cores;
        for(int i = 0; i < workers; i++) {
            MapRed thisRound = RoundFactory.makeRound(round);
            mappers.add(0, thisRound.map);
            reducers.add(thisRound.reduce);
        }
        
        // start maps
        for(int i = 0; i < workers; i++) {
            mappers.get(i).start();
        }
        //end maps
        for(int i = 0; i < workers; i++) {
           try {
                mappers.get(i).join();
            } catch(InterruptedException e) {
                e.printStackTrace();
            } 
        }
        
        // start reds
        for(int i = 0; i < workers; i++) {
            reducers.get(i).start();
        }
        //end reds
        for(int i = 0; i < workers; i++) {
           try {
                reducers.get(i).join();
            } catch(InterruptedException e) {
                e.printStackTrace();
            } 
        }
        
    }

    private long countCliques() {
        int cores = Runtime.getRuntime().availableProcessors();
        runRound(1, cores);
        Round1Map.graph = Round1Map.save;
        Round1Map.save = new ConcurrentHashMap<Integer, Edge>();
        runRound(2, cores); 
        runRound(3, cores); 
        runRound(4, cores); 
        runRound(5, cores); 
        long cliques = 0;
        for(long i : Round5Red.result.values()) {
            if(cliques > cliques + i) {
                return Long.MAX_VALUE;
            } else {
                cliques += i;
            }
        }
        return cliques;
    }

    public void start() {

        int t0 = 10, t1 = 1000;
        long interval = 120000L;
        graph2d = client.getGraph();
        graph = new ArrayList<Edge>();
        createGraph();
        long cliques = countCliques();
        long current = Long.MAX_VALUE;                                      
        currentSize = client.getCurrentSize();
        try {
            history.setCurrentSize(currentSize);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
        TABU_CAP = currentSize * 10;
        long timestamp = System.currentTimeMillis();
        Random rand = new Random(timestamp);
        
        while(cliques != 0) {
            long haha = System.currentTimeMillis();
            if(haha - timestamp >= interval) {
                client.updateFromAlg(currentSize, cliques, graph2d);
                if(currentSize < client.getCurrentSize() ||
                        cliques - client.getCliqueSize() > 10) {
                    return;
                } else {
                    timestamp = System.currentTimeMillis();
                }
            }
            current = getRandomNeighbor();
            if(current < cliques) { 
                accept();
                if(current <= cliques / 10 * 9 || current < 1000) {
                    client.updateFromAlg(currentSize, current, graph2d);
                    if(currentSize < client.getCurrentSize() ||
                            current > client.getCliqueSize()) {
                        return;
                    }
                }
                cliques = current;                                            
            } else {                                                          
                double prob =                                                 
                    Math.pow(Math.E, ((double)(cliques - current))/((double)t1));
                if(prob >= rand.nextDouble() + 0.0000001) { 
                    accept();
                    cliques = current;
                }                                                             
                t1 -= 1;
                t1 = Math.min(t1, t0);
            }
            updateTabu(this.change);
        }
        client.updateFromAlg(currentSize, cliques, graph2d);
    }

    public static void main( String[] args ) {

        Alg excalibur = null;
        try 
        { 
           Registry registry = LocateRegistry.getRegistry(RemoteBloomFilter.PORT);
           Alg.history= (RemoteBloomFilter) 
                                   registry.lookup(RemoteBloomFilter.SERVICE_NAME);
        } 
        catch (Exception e) 
        { 
           e.printStackTrace(); 
        } 
        while(true) {
            excalibur = new Alg();
            try {
                if(history.getCurrentSize() < client.getCurrentSize()) {
                    history.refresh(client.getCurrentSize());
                }
            } catch(RemoteException e) {
                e.printStackTrace();
            }
            excalibur.start();
        }
    }
}
