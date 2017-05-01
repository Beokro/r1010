package search10;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.BlockingQueue;
import java.util.List;
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
    public static int[][] graph2d;
    private int currentSize;
    private int change = -1;

    Alg(String destHost, int destPort) {
        client = new TcpClient(destHost, destPort);
        currentSize = client.getCurrentSize();
    }

    Edge flip(Edge input) {
        if(input.node1 < currentSize) {
            return new Edge(input.node1 + currentSize, input.node2 + currentSize);
        } else {
            return new Edge(input.node1 - currentSize, input.node2 - currentSize);
        }
    }

    private static int getBestNeighbor() {

    }

    private static int getRandomNeighbor() {
        Random rand = new Random(System.currentTimeMillis());             
        int change = rand.nextInt(currentSize());
        while(tabuSet.contains(change)) {
            change = rand.nextInt(currentSize());
        }
        this.change = change;
        Round1Map.graph.set(change, flip(Round1Map.graph.get(change)));
        return countCliques();
    }

    private static void createGraph() {
        int size = graph2d.length;
        Round1Map.graph = new LinkedBlockingQueue<>();
        for(int i = 0; i < size; i++) {
            for(int j = i + 1; j < size; j++) {
                if(graph2d[i][j] == 1) {
                    graph.add(new Edge(i, j));
                    Round1Map.save.offer(new Edge(i, j));
                } else {
                    graph.add(new Edge(i + size, j + size));
                    Round1Map.save.offer(new Edge(i + size, j + size));
                }
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
        List<Thread> mappers = new ArrayList<>();
        List<Thread> reducers = new ArrayList<>();
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

    private int countCliques() {
        int cores = Runtime.getRuntime().availableProcessors();
        Round1Map.graph = Round1Map.save;
        runRound(1, cores); 
        Round1Map.graph = Round1Map.save;
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
        Alg.graph2d = new int[13][];
        for(int i = 0; i < 13; i++) {
            Alg.graph2d[i] = new int[13];
        }
        for(int i = 0; i < 13; i++) {
            for(int j = 0; j < 13; j++) {
                Alg.graph2d[i][j] = 0;
            }
        }
        Alg.createGraph();

        int t0 = 5, t1 = 10000;                                                
        int cliques = countCliques();
        int TABU_CAP = Math.pow(currentSize(), 2);
        int tabuSize = 0;                                                     
        int current = Integer.MAX_VALUE;                                      
        Set<Integer> tabuSet = new HashSet<>();                                
        List<Integer> tabuList = new LinkedList<>();                           
        Random rand = new Random(System.currentTimeMillis());             
        while(cliques != 0) {                                                 
            int n = rand.nextInt(1);                                          
            if(n == 0) {                                                      
                current = getRandomNeighbor();
            } else {                                                          
                current = getBestNeighbor();                                  
            }                                                                 
            if(current <= cliques) {                                          
                cliques = current;                                            
            } else {                                                          
                double prob =                                                 
                    Math.power(Math.E, ((double)(current - cliques))/((double)t1));
                if(prob - rand.nextDouble() >= 0.0001) {                      
                    cliques = current;                                        
                }                                                             
                t1 -= 1;                                                      
                t1 = Math.min(t1, t0);                                        
            }                                                                 
            updateTabu(tabuSet, tabuList);                                    
        }   
    }
}
