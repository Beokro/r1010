package search10;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;


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
    private String serverIp;
    private String bloomFilterIp;
    public static TcpClient client = null;
    public List<Edge> graph;
    public Map<Edge, Integer> edgeToIndex;
    public int[][] graph2d;
    private int currentSize;
    
    static RemoteBloomFilter history;
    static long lowerRestart;
    static long upperRestart;
    static long divFactor;
    static AdjListGraph g;
    static String vertexInG;
    static long maxCliqueChange;
    static Object lock = new Object();

    Alg(String serverIp, String bloomFilterIp) {
        graph = new ArrayList<Edge>();
        edgeToIndex = new HashMap<Edge, Integer>();
        this.serverIp = serverIp;
        this.bloomFilterIp = bloomFilterIp;
        lowerRestart = 1;
        upperRestart = 100;
        divFactor = 2000;
    }

    Edge flip(Edge input) {
        if(input.node1 < currentSize) {
            return new Edge(input.node1 + currentSize, input.node2 + currentSize);
        } else {
            return new Edge(input.node1 - currentSize, input.node2 - currentSize);
        }
    }

    private void addHistory() {
        try {
            history.addHistory(graph2d);
        } catch(RemoteException e) {
            try{
                Registry registry = LocateRegistry.getRegistry(
                        bloomFilterIp, RemoteBloomFilter.PORT);
                history= (RemoteBloomFilter) 
                    registry.lookup(RemoteBloomFilter.SERVICE_NAME);
            } catch(Exception nima) {

            }
        }
    }
    
    private boolean hasVisited() {
        boolean result = false;
        try {
            result = history.inHistory(graph2d);
        } catch(RemoteException e) {
            try{
                Registry registry = LocateRegistry.getRegistry(
                        bloomFilterIp, RemoteBloomFilter.PORT);
                history= (RemoteBloomFilter) 
                    registry.lookup(RemoteBloomFilter.SERVICE_NAME);
            } catch(Exception nima) {

            }
        }
        return result;
    }
    
    private long getRandomNeighbor() {
        
        Random rand = new Random(System.currentTimeMillis());
        List<String> neighbors = Alg.g.getLargerNeighbors(Alg.vertexInG);
        List<String> changeList = null;
        do {
            int numChanges = rand.nextInt(Math.min(neighbors.size() / 2, (int)Alg.maxCliqueChange)) + 1;
            if(numChanges < 0) {
                numChanges = neighbors.size() / 2 + 1;
            }
            java.util.Collections.shuffle(neighbors);
            changeList = neighbors.subList(0, numChanges);
            for(String nei : changeList) {
                int node1 = Integer.parseInt(Alg.vertexInG);
                int node2 = Integer.parseInt(nei);
                Edge edge = new Edge(Math.min(node1, node2), Math.max(node1, node2));
                int index = edgeToIndex.get(edge);
                Round1Map.graph.put(index, flip(edge)); 
                graph.set(index, flip(edge));
                edgeToIndex.put(flip(edge), index);
                edgeToIndex.remove(edge);
                if(edge.node1 >= currentSize) {
                    edge = flip(edge);
                }
                graph2d[edge.node1][edge.node2] = Math.abs(graph2d[edge.node1][edge.node2] - 1);
            }
        } while(hasVisited());
        
        addHistory();
        return countCliques();
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
                    edgeToIndex.put(new Edge(i, j), count);
                } else {
                    graph.add(new Edge(i + size, j + size));
                    Round1Map.graph.put(count, new Edge(i + size, j + size));
                    edgeToIndex.put(new Edge(i + size, j + size), count);
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
    
    boolean useClient(int currentSize, long cliques) {
        long diff = cliques - client.getCliqueSize();
        Random rand = new Random(System.currentTimeMillis());
        long bound = cliques / divFactor;
        double prob =                                                 
                    Math.pow(Math.E, (double)(-diff) / 
               (double)(Math.min(Math.max(bound, lowerRestart), upperRestart)));
        boolean shouldUse = (currentSize < client.getCurrentSize() ||
                (bound >= lowerRestart && 
                diff > Math.min(upperRestart, bound) ) || 
                (bound < lowerRestart && diff > lowerRestart));
        
        //if(shouldUse && prob + 0.0000001 <= rand.nextDouble()) { 
        //    return true;
        //}
        if(shouldUse && rand.nextDouble() > 0.875) {
            return true;
        }
        return false;
    }
    
    public void start() {

        graph2d = client.getGraph();
        currentSize = client.getCurrentSize();
        createGraph();
        long cliques = countCliques();

        while(cliques != 0) {
            cliques = getRandomNeighbor();
            client.updateFromAlg(currentSize, cliques, graph2d);
            if(useClient(currentSize, cliques)) {
                return;
            }
        }
    }

    public static void main( String[] args ) {

        if(args.length < 2) {
            System.out.println("Usage: java -jar <jar file> "
                    + "<server ip> <bloomfilter ip>");
            return;
        }
        String serverIp = args[0];
        String bloomFilterIp = args[1];
        Alg excalibur = null;

        Alg.client = new TcpClient(serverIp, 7788);

        try 
        { 
            Registry registry = LocateRegistry.getRegistry(
                    bloomFilterIp, RemoteBloomFilter.PORT);
            history = (RemoteBloomFilter)
                registry.lookup(RemoteBloomFilter.SERVICE_NAME);
        } 
        catch (Exception e) 
        { 
            e.printStackTrace(); 
        } 

        System.out.println("Alg start");
        while(true) {
            excalibur = new Alg(serverIp, bloomFilterIp);
            excalibur.start();
            try {
                if(history.getCurrentSize() < client.getCurrentSize()) {
                    history.refresh(client.getCurrentSize());
                }
            } catch(RemoteException e) {
                try{
                    Registry registry = LocateRegistry.getRegistry(
                            bloomFilterIp, RemoteBloomFilter.PORT);
                    history= (RemoteBloomFilter) 
                        registry.lookup(RemoteBloomFilter.SERVICE_NAME);
                } catch(Exception nima) {

                }
            }
        }
    }
}
