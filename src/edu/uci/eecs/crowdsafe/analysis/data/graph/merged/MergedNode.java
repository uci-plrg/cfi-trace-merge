package edu.uci.eecs.crowdsafe.analysis.data.graph.merged;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;

public class MergedNode extends Node {
	private final long hash;

	public MergedNode(long hash) {
		this.hash = hash;
	}

}
