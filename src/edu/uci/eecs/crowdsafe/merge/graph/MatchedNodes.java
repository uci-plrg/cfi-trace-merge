package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;

/**
 * MatchedNodes contains pairs of matched nodes. It maintains one-to-one map that maps the matched nodes, an arraylist
 * that records matched nodes in a chronological order. It also records the speculatively matched nodes and its matching
 * confidence in order to rewind the speculative matching.
 * 
 * @author peizhaoo
 * 
 */
public class MatchedNodes {
	private final BiMap<Node.Key, Node.Key> matchedNodesLeftRight = HashBiMap
			.create();

	// Maps index of node1 and its matching score with nodes
	// If they are matched directly, then their score should be 0
	private final Map<Node.Key, Integer> matchingScore = new HashMap<Node.Key, Integer>();

	public void clear() {
		matchedNodesLeftRight.clear();
		matchingScore.clear();
	}

	public Set<Node.Key> getLeftKeySet() {
		return matchedNodesLeftRight.keySet();
	}

	public boolean hasPair(Node.Key leftKey, Node.Key rightKey) {
		if (!matchedNodesLeftRight.containsKey(leftKey)) {
			return false;
		}
		return matchedNodesLeftRight.get(leftKey) == rightKey;
	}

	public boolean addPair(Node left, Node right, int score) {
		Node.Key leftKey = left.getKey();
		Node.Key rightKey = right.getKey();
		if (hasPair(leftKey, rightKey))
			return true;
		if ((matchedNodesLeftRight.containsKey(leftKey) || matchedNodesLeftRight
				.containsValue(rightKey))
				&& !(left.isMetaNode() && right.isMetaNode())) {
			return false;
		}
		matchedNodesLeftRight.put(leftKey, rightKey);
		matchingScore.put(leftKey, score);
		return true;
	}

	public void removeLeftKey(Node.Key leftKey) {
		matchedNodesLeftRight.remove(leftKey);
		matchingScore.remove(leftKey);
	}

	public void removeRightKey(Node.Key rightKey) {
		Node.Key leftKey = matchedNodesLeftRight.inverse().remove(rightKey);
		if (leftKey != null)
			matchingScore.remove(leftKey);
	}

	public boolean containsLeftKey(Node.Key leftKey) {
		return matchedNodesLeftRight.containsKey(leftKey);
	}

	public boolean containsRightKey(Node.Key rightKey) {
		return matchedNodesLeftRight.containsValue(rightKey);
	}

	public Node.Key getMatchByLeftKey(Node.Key leftKey) {
		return matchedNodesLeftRight.get(leftKey);
	}

	public Node.Key getMatchByRightKey(Node.Key rightKey) {
		return matchedNodesLeftRight.inverse().get(rightKey);
	}

	public int getScoreByLeftKey(Node.Key leftKey) {
		return matchingScore.get(leftKey);
	}

	public int size() {
		return matchedNodesLeftRight.size();
	}

	public String toString() {
		return matchedNodesLeftRight.toString();
	}
}