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
    public Map<Edge, Integer> edgeToIndex;
    public int[][] graph2d;
    private int currentSize;
    private double localGamma;
    private double globalGamma;
    final private double gammaForSearch = 0.004;
    final private double gammaForTunnel = 0.001;
    private double betaBase;
    private double globalBetaBase;
    
    static RemoteBloomFilter history;
    static List<String> neighbors;
    static String vertexInG;
    static long maxCliqueChange;
    static Object lock = new Object();

    Alg(String serverIp, String bloomFilterIp) {
        edgeToIndex = new HashMap<Edge, Integer>();
        this.serverIp = serverIp;
        this.bloomFilterIp = bloomFilterIp;
        localGamma = gammaForSearch;
        globalGamma = gammaForTunnel;
        betaBase = 10;
        globalBetaBase = 20;
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

    private void createGraph() {
        int size = graph2d.length;
        Round1Map.graph = new ConcurrentHashMap<Integer, Edge>();
        int count = 0;
        for(int i = 0; i < size; i++) {
            for(int j = i + 1; j < size; j++) {
                if(graph2d[i][j] == 1) {
                    Round1Map.graph.put(count, new Edge(i, j));
                    edgeToIndex.put(new Edge(i, j), count);
                } else {
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
        maxCliqueChange = 0;
        neighbors = new ArrayList<String>();
        vertexInG = "";
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
    
    private boolean useClient(int currentSize, long cliques) {
        
        if(currentSize < client.getCurrentSize()) {
            return true;
        }
        
        long min = client.getCliqueSize();
        
        return notAccept(globalBetaBase, globalGamma, cliques, min, min);
    }
    
    private double adjustBeta(long diff, double betaBase) {
        return betaBase + (double)(diff)/0.1;
    }
    
    private double fstun(double gamma, long cliques, long min) {
        return 1 - Math.pow(Math.E, -gamma*(cliques-min));
    }
    
    private double acceptProb(double betaBase, double gamma, long current, long last, long localMin) {
        double beta = adjustBeta(current - localMin, betaBase);
        return Math.pow(Math.E,
                -beta*(fstun(gamma, current, localMin) - fstun(gamma, last, localMin)));
    }
    
    private boolean notAccept(double betaBase, double gamma, long current, long last, long localMin) {
        if(current <= last) {
            return false;
        }
        Random rand = new Random(System.currentTimeMillis());
        return rand.nextDouble() > acceptProb(betaBase, gamma, current, last, localMin);
    }
    
    private void adjustLocalGamma(List<Double> data) {
        if(data.size() < 500) {
            return;
        }
        DFA dfa = new DFA(data);
        double alpha = dfa.dfa();
        
        if(alpha > 0.75) {
            // local entrapment detected
            localGamma = gammaForTunnel;
        } else {
            localGamma = gammaForSearch;
        }
        data.clear();
    }
    
    private void applyChange(int index) {
        Edge edge = Round1Map.graph.get(index);
        Round1Map.graph.put(index, flip(edge)); 
        edgeToIndex.put(flip(edge), index);
        edgeToIndex.remove(edge);
        if(edge.node1 >= currentSize) {
            edge = flip(edge);
        }
        graph2d[edge.node1][edge.node2] = Math.abs(graph2d[edge.node1][edge.node2] - 1);
    }
    
    private long getAnyNeighbor(List<Integer> changes) {
        Random rand = new Random(System.currentTimeMillis());
        int numChanges = rand.nextInt(currentSize / 30) + 1;
        long bestCliques = client.getCliqueSize();
        int bestChange = -1;
        String saveNode = vertexInG;
        List<String> saveNeis = neighbors;
        long saveCliqueChange = maxCliqueChange;
        for(int i = 0; i < numChanges; i++) {
            int index = rand.nextInt(Round1Map.graph.size());
            applyChange(index);
            if(hasVisited()) {
                applyChange(index);
                continue;
            }
            addHistory();
            long current = countCliques();
            if(current < bestCliques) {
                bestCliques = current;
                bestChange = index;
                saveNode = vertexInG;
                saveNeis = neighbors;
                saveCliqueChange = maxCliqueChange;
            }
            applyChange(index);
        }
        vertexInG = saveNode;
        neighbors = saveNeis;
        maxCliqueChange = saveCliqueChange;
        if(bestChange != -1) {
            applyChange(bestChange);
            changes.add(bestChange);
        }
        return bestCliques;
    }
    
    private long getRandomNeighbor(List<Integer> changes) {
        
        Random rand = new Random(System.currentTimeMillis());
        List<String> temp = new ArrayList<String>(neighbors);
        
        do {
            int numChanges = rand.nextInt(Math.min(temp.size() / 2, (int)Alg.maxCliqueChange)) + 1;
            if(numChanges < 0) {
                numChanges = temp.size() / 2 + 1;
            }
            java.util.Collections.shuffle(temp, rand);
            for(int i = 0; i < numChanges; i++) {
                String nei = temp.get(i);
                int node1 = Integer.parseInt(Alg.vertexInG);
                int node2 = Integer.parseInt(nei);
                Edge edge = new Edge(Math.min(node1, node2), Math.max(node1, node2));
                // if the edge is flipped in the previous round, then flip it back
                if(edgeToIndex.get(edge) == null) {
                    edge = flip(edge);
                }
                int index = edgeToIndex.get(edge);
                applyChange(index);
                changes.add(index);
            }
        } while(hasVisited());
        
        addHistory();
        return countCliques();
    }
    
    public void start() {

        graph2d = client.getGraph();
        currentSize = client.getCurrentSize();
        createGraph();
        long localMin = countCliques();
        client.updateFromAlg(currentSize, localMin, graph2d);
        long lastCliques = localMin;
        long current = localMin;
        boolean accepted = true;
        Random rand = new Random(System.currentTimeMillis());
        List<Double> data = new ArrayList<Double>();
        while(localMin != 0) {
            if(accepted) {
                lastCliques = current;
            }
            List<Integer> changes = new ArrayList<Integer>();
            
            String saveNode = Alg.vertexInG;
            List<String> saveNeis = Alg.neighbors;
            long saveCliqueChange = Alg.maxCliqueChange;
            if(rand.nextInt(2) == 1) {
                current = getRandomNeighbor(changes);
            } else {
                current = getAnyNeighbor(changes);
            }
            
            client.updateFromAlg(currentSize, current, graph2d);
            localMin = Math.min(lastCliques, Math.min(localMin, current));
            data.add((double)(current));
            adjustLocalGamma(data);
            if(notAccept(betaBase, localGamma, current, lastCliques, localMin)) {
                // do changes again to undo them
                for(int change : changes) {
                    applyChange(change);
                }
                vertexInG = saveNode;
                neighbors = saveNeis;
                maxCliqueChange = saveCliqueChange;
                accepted = false;
            } else {
                accepted = true;
            }
            
            if(useClient(currentSize, localMin)) {
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
        System.out.println(Runtime.getRuntime().availableProcessors() + " processors");
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
