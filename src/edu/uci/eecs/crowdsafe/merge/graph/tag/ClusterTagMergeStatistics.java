package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ClusterTagMergeStatistics {

	class HashMismatches {
		final List<Node<?>> left = new ArrayList<Node<?>>();
		final List<Node<?>> right = new ArrayList<Node<?>>();
		
		int size() {
			return left.size();
		}
	}
	
	private int matchCount = 0;

	HashMismatches hashMismatches = new HashMismatches();

	void match() {
		matchCount++;
	}

	public int getMatchCount() {
		return matchCount;
	}

	void hashMismatch(Node<?> left, Node<?> right) {
		hashMismatches.left.add(left);
		hashMismatches.right.add(right);
	}
}
