package edu.uci.eecs.crowdsafe.merge.graph;

public enum GraphMergeStrategy {
	HASH("hash"),
	TAG("tag");

	public final String id;

	private GraphMergeStrategy(String id) {
		this.id = id;
	}

	public static GraphMergeStrategy forId(String id) {
		for (GraphMergeStrategy strategy : GraphMergeStrategy.values()) {
			if (strategy.id.equals(id))
				return strategy;
		}
		return null;
	}
}
