package search10;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

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

class ChangeAndResult implements Comparator<ChangeAndResult> {
    int change;
    long Dcliques;
    
    ChangeAndResult() {}
    
    ChangeAndResult(int change, long Dcliques){
        this.change = change;
        this.Dcliques = Dcliques;
    }
    
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof NodeDegPair)) {
            return false;
        }
        ChangeAndResult temp = (ChangeAndResult)o;
        return temp.change == change;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + this.change;
        return hash;
    }
    
    @Override
    public int compare(ChangeAndResult a, ChangeAndResult b) {
        return (int)(a.Dcliques - b.Dcliques);
    }
}

public class Alg {
    private String serverIp;
    private String bloomFilterIp;
    public static TcpClient client = null;
    public Map<Edge, Integer> edgeToIndex;
    public int[][] graph2d;
    private int currentSize;
    static Object lock1 = new Object();
    static Object lock2 = new Object();
    static Object alarm = new Object();
    static AtomicLong current;
    static Set<ChangeAndResult> bestOptions;
    static int change;
    double globalBetaBase = 10;
    double globalGamma = 0.006;
    
    static BloomFilter history = new BloomFilter();
    static ConcurrentMap<Edge, AtomicLong> edgeToClique =
                                    new ConcurrentHashMap<Edge, AtomicLong>();

    Alg(String serverIp, String bloomFilterIp) {
        edgeToIndex = new HashMap<Edge, Integer>();
        this.serverIp = serverIp;
        this.bloomFilterIp = bloomFilterIp;
        change = -1;
    }
    
    private double adjustBeta(long diff, double betaBase) {
        return betaBase + (double)(diff)*0.2;
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
    
    private boolean useClient(int currentSize, long cliques) {
        
        if(currentSize < client.getCurrentSize()) {
            return true;
        }
        
        long min = client.getCliqueSize();
        
        return notAccept(globalBetaBase, globalGamma, cliques, min, min);
    }
    
    private boolean useServer(int currentSize, long lastTime, long thisTime) {
        if(currentSize < client.getCurrentSize()) { // move on to next step
            return true;
        }
        if(thisTime == 0) { // someone has got 0
            return true;
        }
        if(thisTime < lastTime) { // server not stuck and is not 0
            if(current.get() > thisTime) { // and ours is worse than server's
                return true;
            }
        }
        // else server is stuck so we use our graph
        return false;
    }
    
    class AlgThread extends Thread {
        ConcurrentMap<Edge, AtomicLong> edgeToClique;
        long current;
        AlgThread(ConcurrentMap<Edge, AtomicLong> edgeToClique, AtomicLong current) {
            this.edgeToClique = edgeToClique;
            this.current = current.get();
        }
        
        @Override
        public void run() {
            while(true) {
                Map.Entry<Edge, AtomicLong> entry = null;
                int change = -1;
                Edge e = null;
                synchronized(Alg.lock1) {
                    if(edgeToClique.isEmpty()) {
                        break;
                    }
                    entry = edgeToClique.entrySet().iterator().next();
                    edgeToClique.remove(entry.getKey());
                    e = entry.getKey();
                    change = edgeToIndex.get(e);
                    applyChange(change);
                    if(hasVisited()) {
                        applyChange(change);
                        continue;
                    }
                    applyChange(change);
                }
                
                long Dcliques = countCliquesSubFlip(e) - entry.getValue().get();
                bestOptions.add(new ChangeAndResult(change, Dcliques));
                if(Dcliques < 0) {
                    synchronized(Alg.lock1) {
                        edgeToClique.clear();
                    }
                }
            }
            synchronized(Alg.alarm) {
                Alg.alarm.notify();
            }
        }
    }
    
    public void start() {
        graph2d = client.getGraph();
        currentSize = client.getCurrentSize();
        history.setCurrentSize(currentSize);
        createGraph();
        int cores = Runtime.getRuntime().availableProcessors();
        current = new AtomicLong(Long.MAX_VALUE);
        while(current.get() != 0) {
            bestOptions = new ConcurrentSkipListSet<ChangeAndResult>(new ChangeAndResult());
            current = new AtomicLong(countCliques());
            addHistory();
            client.updateFromAlg(currentSize, current.get(), graph2d);
            List<Thread> workers = new ArrayList<Thread>();
            for(int i = 0; i < cores; i++) {
                workers.add(new AlgThread(edgeToClique, current));
            }
            for(Thread t : workers) {
                t.start();
            }
            try{
                synchronized(alarm) {
                    alarm.wait();
                }
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
            for(Thread t : workers) {
                try {
                    t.join();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Iterator<ChangeAndResult> it = bestOptions.iterator();
            while(it.hasNext()) {
                ChangeAndResult best = it.next();
                applyChange(best.change);
                if(!hasVisited()) {
                    current.set(current.get() + best.Dcliques);
                    break;
                }
                applyChange(best.change);
            }
            long lastTime = client.getCliqueSize();
            client.updateFromAlg(currentSize, current.get(), graph2d);
            long thisTime = client.getCliqueSize();
            
            if(useServer(currentSize, lastTime, thisTime)) {
                return;
            } 
        }
    }
    
    static void recordEdges(int node1, String node2, int[] indexes, List<String> neighbors) {
        List<Integer> nodes =  new ArrayList<Integer>();
        nodes.add(node1);
        nodes.add(Integer.parseInt(node2));
        for(int i = 0; i < indexes.length; i++) {
            String node = neighbors.get(indexes[i]);
            nodes.add(Integer.parseInt(node));
        }
        Collections.sort(nodes);
        for(int i = 0; i < nodes.size(); i++) {
            for(int j = i + 1; j < nodes.size(); j++) {
                Edge edge = new Edge(nodes.get(i), nodes.get(j));
                edgeToClique.putIfAbsent(edge, new AtomicLong());
                edgeToClique.get(edge).incrementAndGet();
            }
        }
    }
    
    Edge flip(Edge input) {
        if(input.node1 < currentSize) {
            return new Edge(input.node1 + currentSize, input.node2 + currentSize);
        } else {
            return new Edge(input.node1 - currentSize, input.node2 - currentSize);
        }
    }

    private void addHistory() {
        history.addHistory(graph2d);
    }
    
    private boolean hasVisited() {
        return history.inHistory(graph2d);
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
        edgeToClique = new ConcurrentHashMap<Edge, AtomicLong>();
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
    
    synchronized private void applyChange(int index) {
        Edge edge = Round1Map.graph.get(index);
        Round1Map.graph.put(index, flip(edge)); 
        edgeToIndex.put(flip(edge), index);
        edgeToIndex.remove(edge);
        if(edge.node1 >= currentSize) {
            edge = flip(edge);
        }
        graph2d[edge.node1][edge.node2] = Math.abs(graph2d[edge.node1][edge.node2] - 1);
    }
    
    private long countCliquesSubFlip(Edge edge) {
        int currentSize = client.getCurrentSize();
        Edge flip = flip(edge);
        List<Integer> intersect = new ArrayList<Integer>();
        if(flip.node1 >= currentSize) {
            for(int i = currentSize; i < currentSize * 2; i++) {
                Edge test1 = new Edge(Math.min(flip.node1, i), Math.max(flip.node1, i));
                Edge test2 = new Edge(Math.min(flip.node2, i), Math.max(flip.node2, i));
                if(edgeToIndex.get(test1) != null && edgeToIndex.get(test2) != null) {
                    intersect.add(i);
                }
            }
        } else {
            for(int i = 0; i < currentSize; i++) {
                Edge test1 = new Edge(Math.min(flip.node1, i), Math.max(flip.node1, i));
                Edge test2 = new Edge(Math.min(flip.node2, i), Math.max(flip.node2, i));
                if(edgeToIndex.get(test1) != null && edgeToIndex.get(test2) != null) {
                    intersect.add(i);
                }
            }
        }
        AdjListGraph g = new AdjListGraph();
        for(int i = 0; i < intersect.size(); i++) {
            for(int j = i + 1; j < intersect.size(); j++) {
                int node1 = intersect.get(i);
                int node2 = intersect.get(j);
                if(node1 >= client.getCurrentSize()) {
                    node1 -= client.getCurrentSize();
                    node2 -= client.getCurrentSize();
                    if(graph2d[Math.min(node1, node2)][Math.max(node1, node2)] == 0) {
                        g.addEdge(Integer.toString(node1 + client.getCurrentSize()), Integer.toString(node2 + client.getCurrentSize()));
                    }
                } else {
                    if(graph2d[Math.min(node1, node2)][Math.max(node1, node2)] == 1) {
                        g.addEdge(Integer.toString(node1), Integer.toString(node2));
                    }
                }
            }
        }
        return g.countCliquesOfSize(8);
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

        System.out.println("Alg start");
        System.out.println(Runtime.getRuntime().availableProcessors() + " processors");
        while(true) {
            excalibur = new Alg(serverIp, bloomFilterIp);
            excalibur.start();
            if(history.getCurrentSize() < client.getCurrentSize()) {
                history.refresh(client.getCurrentSize());
            }
        }
    }
}