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
    public static TcpClient client;
    public static List<Edge> graph = new ArrayList<>();
    public int[][] graph2d;
    private int currentSize;
    private int change = -1;
    Set<Integer> tabuSet = new HashSet<>();                                
    List<Integer> tabuList = new LinkedList<>();                           
    int TABU_CAP;

    Alg(String destHost, int destPort) {
        client = new TcpClient(destHost, destPort);
        client.run();
        currentSize = client.getCurrentSize();
    }

    Edge flip(Edge input) {
        if(input.node1 < currentSize) {
            return new Edge(input.node1 + currentSize, input.node2 + currentSize);
        } else {
            return new Edge(input.node1 - currentSize, input.node2 - currentSize);
        }
    }

    private void updateTabu() {
        tabuList.add(change);
        tabuSet.add(change);
        change = -1;
        if(tabuSet.size() > TABU_CAP) {
            int toDelete = tabuList.get(0);
            tabuList.remove(0);
            tabuSet.remove(toDelete);
        }

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
    
    private int getBestNeighbor() {
        int change = -1;
        int min = Integer.MAX_VALUE;
        for(int i = 0; i < graph.size(); i++) {
            if(tabuSet.contains(i)) {
                continue;
            }
            change = i;
            Edge haha = Round1Map.graph.get(change);
            Round1Map.graph.put(change, flip(Round1Map.graph.get(change))); 
            int current = countCliques();
            Round1Map.graph.put(change, flip(Round1Map.graph.get(change))); 
            if(current < min) {
                min = current;
                this.change = change;
                if(min == 0) {
                    break;
                }
            }
        }
        return min;
    }

    private int getRandomNeighbor() {
        Random rand = new Random(System.currentTimeMillis());             
        int change = rand.nextInt(graph.size());
        while(tabuSet.contains(change)) {
            change = rand.nextInt(currentSize);
        }
        this.change = change;
        Round1Map.graph.put(change, flip(Round1Map.graph.get(change)));
        int result = countCliques();
        Round1Map.graph.put(change, flip(Round1Map.graph.get(change)));
        return result;
    }

    private void createGraph() {
        int size = graph2d.length;
        Round1Map.graph = new ConcurrentHashMap<>();
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
        TABU_CAP = graph.size() / 4;
    }

    public static boolean doubleCheck(int node1, int degree1, int node2, int degree2) {
        if(degree1 < degree2) {
            return true;
        }
        return degree1 == degree2 && node1 < node2;
    }

    private void runRound(int round, int cores) {
        List<Thread> mappers = new ArrayList<>();
        List<Thread> reducers = new ArrayList<>();
        int workers = cores;
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

    private int countCliques() {
        int cores = Runtime.getRuntime().availableProcessors();
        runRound(1, cores); 
        Round1Map.graph = Round1Map.save;
        Round1Map.save = new ConcurrentHashMap<>();
        runRound(2, cores); 
        runRound(3, cores); 
        runRound(4, cores); 
        runRound(5, cores); 
        int cliques = 0;
        for(int i : Round5Red.result.values()) {
            if(cliques > cliques + i) {
                return Integer.MAX_VALUE;
            } else {
                cliques += i;
            }
        }
        return cliques;
    }

    public void start() {

        int t0 = 5, t1 = 100000;
        graph2d = client.getGraph();
        createGraph();
        int cliques = countCliques();
        int current = Integer.MAX_VALUE;                                      
        Random rand = new Random(System.currentTimeMillis());

        while(cliques != 0) {                                                 
            int n = rand.nextInt(2);                                          
            if(n == 0) {
                current = getRandomNeighbor();
                if(current < cliques) { 
                    accept();
                    if(current <= cliques / 2 || current < 10) {
                        client.updateFromAlg(currentSize, current, graph2d);
                    }
                    cliques = current;                                            
                } else {                                                          
                    double prob =                                                 
                        Math.pow(Math.E, ((double)(cliques - current))/((double)t1));
                    if(prob - rand.nextDouble() >= 0.0000001) { 
                        accept();
                        cliques = current;
                    }                                                             
                    t1 -= 1;
                    t1 = Math.min(t1, t0);
                }  
            } else {                                                          
                current = getBestNeighbor();
                cliques = current;
                accept();
            }  
            this.change = -1;
                                                                           
            updateTabu();
        } 
        client.updateFromAlg(currentSize, cliques, graph2d);
    }

    public static void main( String[] args ) {
        
        Alg excalibur = new Alg("98.185.210.172", 7788);
        while(true) {
            excalibur.start();
        }
    }
}
