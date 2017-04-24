package it.Util;

public interface CliqueCounterGraph {

	public long countCliquesOfSize(int cliqueSize);
	public void addEdge (String a, String b);
	public int getNodesNumber();
}
