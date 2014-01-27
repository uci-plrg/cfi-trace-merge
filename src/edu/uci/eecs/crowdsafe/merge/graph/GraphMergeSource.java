package edu.uci.eecs.crowdsafe.merge.graph;

public enum GraphMergeSource {
	LEFT("left"),
	RIGHT("right");

	public final String label;

	private GraphMergeSource(String label) {
		this.label = label;
	}
}
