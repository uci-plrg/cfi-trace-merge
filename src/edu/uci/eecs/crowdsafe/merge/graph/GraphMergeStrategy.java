package edu.uci.eecs.crowdsafe.merge.graph;

import edu.uci.eecs.crowdsafe.graph.data.DataMessageType;


public enum GraphMergeStrategy {
	HASH("hash", 0),
	TAG("tag", 1);

	public final String id;
	public final int resultsId;

	private GraphMergeStrategy(String id, int resultsId) {
		this.id = id;
		this.resultsId = resultsId;
	}

	public DataMessageType getMessageType() {
		switch (this) {
			case HASH:
				return DataMessageType.HASH_MERGE_RESULTS;
			case TAG:
				return DataMessageType.TAG_MERGE_RESULTS;
		}
		throw new IllegalArgumentException("Unknown merge strategy " + this);
	}

	public static GraphMergeStrategy forId(String id) {
		for (GraphMergeStrategy strategy : GraphMergeStrategy.values()) {
			if (strategy.id.equals(id))
				return strategy;
		}
		return null;
	}
}
