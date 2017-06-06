package search10;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MatrixGraph{
    int[][] graph;
    int size;
    public Map<Edge, Long> edgeToClique;
    
    MatrixGraph(int size) {
        this.size = size * 2;
        graph = new int[this.size][];
        for(int i = 0; i < this.size; i++) {
            graph[i] = new int[this.size];
        }
    }

    public void addEdge(int node1, int node2) {
        if(node1 < node2) {
            graph[node1][node2] = 1;
        } else {
            graph[node2][node1] = 1;
        }
    }

    private long count8FromTo(int start, int size, boolean change) {
        long count = 0;
        for(int i = start; i < size; i++) {
        for(int j = i + 1; j < size; j++) {
        for(int k = j + 1; k < size; k++) {
        if(1 == graph[i][j] && 1 == graph[j][k] && 1 == graph[i][k]){
        for(int l = k + 1; l < size; l++) {
        if(1 == graph[i][l] && 1 == graph[j][l] && 1 == graph[k][l]) {
        for(int m = l + 1; m < size; m++) {
        if(1 == graph[i][m] && 1 == graph[j][m] && 1 == graph[k][m] && 1 == graph[l][m]) {
        for(int n = m + 1; n < size; n++) {
        if(1 == graph[i][n] && 1 == graph[j][n] && 1 == graph[k][n] && 1 == graph[l][n] && 1 == graph[m][n]) {
        for(int o = n + 1; o < size; o++) {
        if(1 == graph[i][o] && 1 == graph[j][o] && 1 == graph[k][o] && 1 == graph[l][o] && 1 == graph[m][o] && 1 == graph[n][o]) {
        for(int p = o + 1; p < size; p++) {
        if(1 == graph[i][p] && 1 == graph[j][p] && 1 == graph[k][p] && 1 == graph[l][p] && 1 == graph[m][p] && 1 == graph[n][p] && 1 == graph[o][p]) {
            if(change) {
                List<Integer> nodes = new ArrayList<>(Arrays.asList(i, j, k, l, m, n, o, p));
                Alg.recordEdges(nodes, edgeToClique);
            }
            count += 1;
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        return count;
    }
    
    public long count8Cliques(boolean change) {
        /*
        long count = 0;
        for(int i = 0; i < size; i++) {
        for(int j = i + 1; j < size; j++) {
        for(int k = j + 1; k < size; k++) {
        if(1 == graph[i][j] && 1 == graph[j][k] && 1 == graph[i][k]){
        for(int l = k + 1; l < size; l++) {
        if(1 == graph[i][l] && 1 == graph[j][l] && 1 == graph[k][l]) {
        for(int m = l + 1; m < size; m++) {
        if(1 == graph[i][m] && 1 == graph[j][m] && 1 == graph[k][m] && 1 == graph[l][m]) {
        for(int n = m + 1; n < size; n++) {
        if(1 == graph[i][n] && 1 == graph[j][n] && 1 == graph[k][n] && 1 == graph[l][m] && 1 == graph[m][n]) {
        for(int o = n + 1; o < size; o++) {
        if(1 == graph[i][o] && 1 == graph[j][o] && 1 == graph[k][o] && 1 == graph[l][o] && 1 == graph[m][o] && 1 == graph[n][o]) {
        for(int p = o + 1; p < size; p++) {
        if(1 == graph[i][p] && 1 == graph[j][p] && 1 == graph[k][p] && 1 == graph[l][p] && 1 == graph[m][p] && 1 == graph[n][p] && 1 == graph[o][p]) {
            if(change) {
                List<Integer> nodes = new ArrayList<>(Arrays.asList(i, j, k, l, m, n, o, p));
                Alg.recordEdges(nodes, edgeToClique);
            }
            count += 1;
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        }
        return count;
        */
        return count8FromTo(0, size / 2, change) + count8FromTo(size / 2, size, change);
    }
}
