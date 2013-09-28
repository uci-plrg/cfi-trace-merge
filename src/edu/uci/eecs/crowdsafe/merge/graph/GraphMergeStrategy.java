package edu.uci.eecs.crowdsafe.merge.graph;

public enum GraphMergeStrategy {
	HASH("hash", 0),
	TAG("tag", 1);

	public final String id;
	public final int resultsId;

	private GraphMergeStrategy(String id, int resultsId) {
		this.id = id;
		this.resultsId = resultsId;
	}

	public static GraphMergeStrategy forId(String id) {
		for (GraphMergeStrategy strategy : GraphMergeStrategy.values()) {
			if (strategy.id.equals(id))
				return strategy;
		}
		return null;
	}
}
