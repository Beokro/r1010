package search10;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

class CliqueId {
    long count;
    CliqueId(long count) {
        this.count = count;
    }
}

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

class Edge implements java.io.Serializable {
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
    Edge change;
    long Dcliques;
    
    ChangeAndResult() {}
    
    ChangeAndResult(Edge change, long Dcliques){
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
        int hash = 3;
        hash = 71 * hash + Objects.hashCode(this.change);
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
    public int[][] graph2d;
    private int currentSize;
    static Object lock1 = new Object();
    static Object lock2 = new Object();
    static Object alarm = new Object();
    static AtomicLong current;
    static Set<ChangeAndResult> bestOptions;
    double globalBetaBase = 10;
    double globalGamma = 0.006;
    
    static RemoteBloomFilter history;
    static ConcurrentMap<Edge, AtomicLong> edgeToClique;
    static ConcurrentMap<Edge, AtomicLong> save;
    
    Alg(String serverIp, String bloomFilterIp) {
        this.serverIp = serverIp;
        this.bloomFilterIp = bloomFilterIp;
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
        return false;
    }
    
    class AlgThread extends Thread {
        ConcurrentMap<Edge, AtomicLong> edgeToClique;
        AlgThread(ConcurrentMap<Edge, AtomicLong> edgeToClique) {
            this.edgeToClique = edgeToClique;
        }
        
        @Override
        public void run() {
            while(true) {
                Map.Entry<Edge, AtomicLong> entry = null;
                Edge e = null;
                synchronized(Alg.lock1) {
                    if(edgeToClique.isEmpty()) {
                        break;
                    }
                    entry = edgeToClique.entrySet().iterator().next();
                    edgeToClique.remove(entry.getKey());
                    e = entry.getKey();
                }
                /*
                if(hasVisited(e)) {
                    continue;
                }
                */
                long Dcliques = countCliquesSub(flip(e), false, null) - entry.getValue().get();
                bestOptions.add(new ChangeAndResult(e, Dcliques));
                if(Dcliques < 0 && !hasVisited(e)) {
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
    
    private void getRandomNeighbor() {
        Random rand = new Random(System.currentTimeMillis());
        Edge edge = new Edge(rand.nextInt(currentSize), rand.nextInt(currentSize));
        if(!hasEdge(edge)) {
            edge = flip(edge);
        }
        applyChange(edge);
    }
    
    private void applyChange(Edge edge) {
        flipEdge(edge);
        updateEdgeToClique(edge);
    }

    private void saveEdgeToClique() {
        save = new ConcurrentHashMap<>();
        for(Map.Entry<Edge, AtomicLong> entry : edgeToClique.entrySet()) {
            save.put(entry.getKey(), entry.getValue());
        }
    }
    
    public void start() {
        if(!client.getValidMap()) {
            graph2d = client.getGraph();
            currentSize = client.getCurrentSize();
            createGraph();
            edgeToClique = new ConcurrentHashMap<>();
            current = new AtomicLong(countCliques());
            client.updateFromAlg(client.getCurrentSize(), current.get(),
                                    graph2d, (ConcurrentHashMap)edgeToClique);
            
        }
        graph2d = client.getGraph();
        currentSize = client.getCurrentSize();
        edgeToClique = (ConcurrentMap)client.getMap();
        int cores = Runtime.getRuntime().availableProcessors();
        current = new AtomicLong(client.getCliqueSize());
        //client.updateFromAlg(currentSize, current.get(), graph2d, (ConcurrentHashMap)edgeToClique);
        long lastTime = client.getCliqueSize();
        long thisTime = Long.MAX_VALUE;
        while(current.get() != 0) {
            bestOptions = new ConcurrentSkipListSet<ChangeAndResult>(new ChangeAndResult());
            saveEdgeToClique();
            /*
            createGraph();
            long haha = countCliques();
            assert(current.get() == haha);
            assert(save.size() == edgeToClique.size());
            for(Map.Entry<Edge, AtomicLong> entry : edgeToClique.entrySet()) {
                assert(save.containsKey(entry.getKey()));
                assert(save.get(entry.getKey()).get() == entry.getValue().get());
            }
            */
            List<Thread> workers = new ArrayList<Thread>();
            for(int i = 0; i < cores; i++) {
                workers.add(new AlgThread(edgeToClique));
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
            edgeToClique = save;
            Iterator<ChangeAndResult> it = bestOptions.iterator();
            boolean hasResult = false;
            while(it.hasNext()) {
                ChangeAndResult best = it.next();
                if(!hasVisited(best.change)) {
                    applyChange(best.change);
                    hasResult = true;
                    break;
                }
            }
            if(!hasResult) {
                getRandomNeighbor();
            }
            addHistory();
            client.updateFromAlg(currentSize, current.get(),
                                    graph2d, (ConcurrentHashMap)edgeToClique);
            thisTime = client.getCliqueSize();
            
            if(useServer(currentSize, lastTime, thisTime)) {
                return;
            }
            lastTime = thisTime;
        }
    }
    
    Edge flip(Edge input) {
        if(input.node1 < currentSize) {
            return new Edge(input.node1 + currentSize, input.node2 + currentSize);
        } else {
            return new Edge(input.node1 - currentSize, input.node2 - currentSize);
        }
    }

    private void flipEdge(Edge edge) {
        if(edge.node1 >= currentSize) {
            edge = flip(edge);
        }
        graph2d[edge.node1][edge.node2] = Math.abs(graph2d[edge.node1][edge.node2] - 1);
    }
    
    void changeEdgeToClique(Edge edge, long delta) {
        if(delta <= 0) {
            edgeToClique.get(edge).addAndGet(delta);
            if(edgeToClique.get(edge).get() == 0) {
                edgeToClique.remove(edge);
            }
        } else {
            edgeToClique.putIfAbsent(edge, new AtomicLong());
            edgeToClique.get(edge).addAndGet(delta);
        }
        
    }
    
    void updateEdgeToClique(Edge edge) {
        edgeToClique.remove(edge);
        Edge flip = flip(edge);
        Map<Edge, Long> minus = new HashMap<>();
        Map<Edge, Long> plus = new HashMap<>();
        Map<Integer, Long> nodesMinus = new HashMap<>();
        Map<Integer, Long> nodesPlus = new HashMap<>();
        long oldCount = countCliquesSub(edge, true, minus);
        long newCount = countCliquesSub(flip, true, plus);
        for(Map.Entry<Edge, Long> entry : minus.entrySet()) {
            changeEdgeToClique(entry.getKey(), -entry.getValue());
            nodesMinus.putIfAbsent(entry.getKey().node1, new Long(0));
            nodesMinus.putIfAbsent(entry.getKey().node2, new Long(0));
            nodesMinus.put(entry.getKey().node1, nodesMinus.get(entry.getKey().node1) + entry.getValue());
            nodesMinus.put(entry.getKey().node2, nodesMinus.get(entry.getKey().node2) + entry.getValue());
        }
        for(Map.Entry<Edge, Long> entry : plus.entrySet()) {
            changeEdgeToClique(entry.getKey(), entry.getValue());
            nodesPlus.putIfAbsent(entry.getKey().node1, new Long(0));
            nodesPlus.putIfAbsent(entry.getKey().node2, new Long(0));
            nodesPlus.put(entry.getKey().node1, nodesPlus.get(entry.getKey().node1) + entry.getValue());
            nodesPlus.put(entry.getKey().node2, nodesPlus.get(entry.getKey().node2) + entry.getValue());
        }
        for(Map.Entry<Integer, Long> entry : nodesMinus.entrySet()) {
            Edge temp1 = new Edge(Math.min(edge.node1, entry.getKey()),
                                  Math.max(edge.node1, entry.getKey()));
            Edge temp2 = new Edge(Math.min(edge.node2, entry.getKey()),
                                  Math.max(edge.node2, entry.getKey()));
            changeEdgeToClique(temp1, -entry.getValue() / 7);
            changeEdgeToClique(temp2, -entry.getValue() / 7);
        }
        for(Map.Entry<Integer, Long> entry : nodesPlus.entrySet()) {
            Edge temp1 = new Edge(Math.min(flip.node1, entry.getKey()),
                                  Math.max(flip.node1, entry.getKey()));
            Edge temp2 = new Edge(Math.min(flip.node2, entry.getKey()),
                                  Math.max(flip.node2, entry.getKey()));
            changeEdgeToClique(temp1, entry.getValue() / 7);
            changeEdgeToClique(temp2, entry.getValue() / 7);
        }
        if(newCount != 0) {
            edgeToClique.put(flip, new AtomicLong(newCount));
        }
        current.set(current.get() + newCount - oldCount);
    }

    static void recordEdges(int node1, String node2, int[] indexes,
                        List<String> neighbors, Map<Edge, Long> edgeToClique) {
        List<Integer> nodes = new ArrayList<Integer>();
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
                edgeToClique.putIfAbsent(edge, new Long(0));
                edgeToClique.put(edge, edgeToClique.get(edge) + 1);
            }
        }
    }
    
    static void recordEdges(String node, int[] indexes, List<String> neighbors,
            Map<Edge, Long> edgeToClique, boolean change) {
        if(!change) {
            return;
        }
        List<Integer> nodes =  new ArrayList<Integer>();
        nodes.add(Integer.parseInt(node));
        for(int i = 0; i < indexes.length; i++) {
            String temp = neighbors.get(indexes[i]);
            nodes.add(Integer.parseInt(temp));
        }
        Collections.sort(nodes);
        for(int i = 0; i < nodes.size(); i++) {
            for(int j = i + 1; j < nodes.size(); j++) {
                Edge edge = new Edge(nodes.get(i), nodes.get(j));
                edgeToClique.putIfAbsent(edge, new Long(0));
                edgeToClique.put(edge, edgeToClique.get(edge) + 1);
            }
        }
    }
    
    private void addHistory() {
        try{
            history.addHistory(graph2d);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
    }
    /*
    static synchronized UUID nextCliqueId() {
        return UUID.randomUUID();
    }
    */
    private boolean hasEdge(Edge edge) {
        if(edge.node1 >= currentSize) {
            return graph2d[edge.node1 - currentSize][edge.node2 - currentSize] == 0;
        }
        return graph2d[edge.node1][edge.node2] == 1;
    }

    private synchronized boolean hasVisited(Edge edge) {
        boolean result = false;
        flipEdge(edge);
        try{
            result = history.inHistory(graph2d);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
        flipEdge(edge);
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
                } else {
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
        for(int i = 0; i < currentSize; i++) {
            for(int j = i + 1; j < currentSize; j++) {
                edgeToClique.put(new Edge(i, j), new AtomicLong());
                edgeToClique.put(new Edge(i + currentSize, j + currentSize), new AtomicLong());
            }
        }
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
        Iterator<Map.Entry<Edge, AtomicLong>> it = edgeToClique.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<Edge, AtomicLong> entry = it.next();
            if(entry.getValue().get() == 0) {
                it.remove();
            }
        }
        return cliques;
    }

    List<Integer> getIntersectNodes(Edge edge) {
        List<Integer> intersect = new ArrayList<Integer>();
        if(edge.node1 >= currentSize) {
            for(int i = currentSize; i < currentSize * 2; i++) {
                if(i == edge.node1 || i == edge.node2) {
                    continue;
                }
                Edge test1 = new Edge(Math.min(edge.node1, i), Math.max(edge.node1, i));
                Edge test2 = new Edge(Math.min(edge.node2, i), Math.max(edge.node2, i));
                if(hasEdge(test1) && hasEdge(test2)) {
                    intersect.add(i);
                }
            }
        } else {
            for(int i = 0; i < currentSize; i++) {
                if(i == edge.node1 || i == edge.node2) {
                    continue;
                }
                Edge test1 = new Edge(Math.min(edge.node1, i), Math.max(edge.node1, i));
                Edge test2 = new Edge(Math.min(edge.node2, i), Math.max(edge.node2, i));
                if(hasEdge(test1) && hasEdge(test2)) {
                    intersect.add(i);
                }
            }
        }
        return intersect;
    }
    
    private long countCliquesSub(Edge edge, boolean change, Map<Edge, Long> edgeToClique) {
        int currentSize = client.getCurrentSize();
        List<Integer> intersect = getIntersectNodes(edge);
        AdjListGraph g = new AdjListGraph();
        g.edgeToClique = edgeToClique;
        for(int i = 0; i < intersect.size(); i++) {
            for(int j = i + 1; j < intersect.size(); j++) {
                int node1 = intersect.get(i);
                int node2 = intersect.get(j);
                if(node1 > node2) {
                    int temp = node1;
                    node1 = node2;
                    node2 = temp;
                }
                if(hasEdge(new Edge(node1, node2))) {
                    g.addEdge(Integer.toString(node1), Integer.toString(node2));
                }
            }
        }
        return g.countCliquesOfSize(8, change);
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
            try {
                if(history.getCurrentSize() < client.getCurrentSize()) {
                    history.refresh(client.getCurrentSize());
                }
            } catch(RemoteException e) {
                e.printStackTrace();
            }
            excalibur = new Alg(serverIp, bloomFilterIp);
            excalibur.start();
            
        }
    }
}