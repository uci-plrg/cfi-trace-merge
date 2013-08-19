package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;


/**
 * MatchedNodes contains pairs of matched nodes. It maintains one-to-one map
 * that maps the matched nodes, an arraylist that records matched nodes in a
 * chronological order. It also records the speculatively matched nodes and its
 * matching confidence in order to rewind the speculative matching.
 * 
 * @author peizhaoo
 * 
 */
public class MatchedNodes implements Iterable<Integer> {
	private HashMap<Integer, Integer> matchedNodes12, matchedNodes21;

	// Maps index of node1 and its matching score with nodes
	// If they are matched directly, then their score should be 0
	private HashMap<Integer, Integer> matchingScore;

	public MatchedNodes() {
		matchedNodes12 = new HashMap<Integer, Integer>();
		matchedNodes21 = new HashMap<Integer, Integer>();
		matchingScore = new HashMap<Integer, Integer>();
	}

	public String toString() {
		return matchedNodes12.toString();
	}

	public Iterator<Integer> iterator() {
		return matchedNodes12.keySet().iterator();
	}

	public boolean hasPair(int index1, int index2) {
		if (!matchedNodes12.containsKey(index1)) {
			return false;
		}
		if (matchedNodes12.get(index1) == index2) {
			return true;
		} else {
			return false;
		}
	}

	public boolean addPair(int index1, int index2, int score) {
		if (hasPair(index1, index2))
			return true;
		if (matchedNodes12.containsKey(index1)
				|| matchedNodes21.containsKey(index2)) {
			return false;
		}
		matchedNodes12.put(index1, index2);
		matchedNodes21.put(index2, index1);

		matchingScore.put(index1, score);

		return true;
	}

	public void removeByFirstIndex(int index1) {
		int index2 = matchedNodes12.get(index1);
		matchedNodes12.remove(index1);
		matchedNodes21.remove(index2);
		matchingScore.remove(index1);
	}

	public void removeBySecondIndex(int index2) {
		int index1 = matchedNodes21.get(index2);
		matchedNodes12.remove(index1);
		matchedNodes21.remove(index2);
		matchingScore.remove(index1);
	}

	public boolean containsKeyByFirstIndex(int index1) {
		return matchedNodes12.containsKey(index1);
	}

	public boolean containsKeyBySecondIndex(int index2) {
		return matchedNodes21.containsKey(index2);
	}

	public Integer getByFirstIndex(int index1) {
		return matchedNodes12.get(index1);
	}

	public Integer getBySecondIndex(int index2) {
		return matchedNodes21.get(index2);
	}

	public int getScoreByFirstIndex(int index1) {
		return matchingScore.get(index1);
	}

	public int size() {
		return matchedNodes12.size();
	}
}