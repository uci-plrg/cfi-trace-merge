package edu.uci.plrg.cfi.x86.merge.graph;

public enum GraphMergeSource {
	LEFT("left"),
	RIGHT("right"),
	DATASET("dataset");

	public final String label;

	private GraphMergeSource(String label) {
		this.label = label;
	}
}
