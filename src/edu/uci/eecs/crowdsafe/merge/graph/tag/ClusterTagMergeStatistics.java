package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.graph.data.graph.Node;

public class ClusterTagMergeStatistics {

	class Mismatches {
		final List<Node<?>> left = new ArrayList<Node<?>>();
		final List<Node<?>> right = new ArrayList<Node<?>>();

		int size() {
			return left.size();
		}
	}

	private int addedNodes = 0;
	private int matchedEdges = 0;
	private int addedEdges = 0;

	Mismatches hashMismatches = new Mismatches();
	Mismatches edgeMismatches = new Mismatches();

	void nodeAdded() {
		addedNodes++;
	}

	public int getAddedNodeCount() {
		return addedNodes;
	}

	void edgeMatched() {
		matchedEdges++;
	}

	public int getMatchedEdgeCount() {
		return matchedEdges;
	}

	void edgeAdded() {
		addedEdges++;
	}

	public int getAddedEdgeCount() {
		return addedEdges;
	}

	void hashMismatch(Node<?> left, Node<?> right) {
		hashMismatches.left.add(left);
		hashMismatches.right.add(right);
	}

	void edgeMismatch(Node<?> left, Node<?> right) {
		edgeMismatches.left.add(left);
		edgeMismatches.right.add(right);
	}
}
