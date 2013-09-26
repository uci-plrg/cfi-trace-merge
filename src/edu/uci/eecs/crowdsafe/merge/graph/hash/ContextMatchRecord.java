package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ContextMatchRecord {

	public enum EdgeMatchType {
		ONE_SIDE_ONLY,
		DIRECT_MATCH,
		INDIRECT_UNIQUE_MATCH,
		INDIRECT_AMBIGUOUS_MATCH,
		INDIRECT_DIVERGENCE
	}

	private static class EdgeComparison {
		int depth;
		EdgeMatchType type;
	}

	public static class State {
		private int index;
		private int matchedNodeCount;
		private int comparedNodeCount;
		private boolean fail;
		private boolean mismatch;
		private boolean reachedTargetDepth;
		private boolean hasAmbiguity;
		private boolean complete;

		void copyTo(State target) {
			target.index = index;
			target.matchedNodeCount = matchedNodeCount;
			target.comparedNodeCount = comparedNodeCount;
			target.reachedTargetDepth = reachedTargetDepth;
			target.hasAmbiguity = hasAmbiguity;
			target.complete = complete;
			target.fail = false;
		}
	}

	private static final int INITIAL_COMPARISON_COUNT = 100;
	private static final int INITIAL_STATE_COUNT = 20;

	private final State currentState = new State();

	private final List<EdgeComparison> edges = new ArrayList<EdgeComparison>(INITIAL_COMPARISON_COUNT);

	// In case of recursively compute the similarity of cyclic graph, record
	// the compared nodes every time getContextSimilarity is called
	private final Set<Node<?>> comparedNodeSet = new HashSet<Node<?>>();
	private final List<Node<?>> comparedNodeList = new ArrayList<Node<?>>();

	private int stateIndex;
	private final List<State> stateStack = new ArrayList<State>();
	
	private Node<?> leftSubtreeRoot;
	private Node<?> rightSubtreeRoot;

	public ContextMatchRecord() {
		for (int i = 0; i < INITIAL_COMPARISON_COUNT; i++) {
			edges.add(new EdgeComparison());
		}
		for (int i = 0; i < INITIAL_STATE_COUNT; i++) {
			stateStack.add(new State());
		}
	}

	public void reset(Node<?> leftSubtreeRoot, Node<?> rightSubtreeRoot) {
		currentState.index = 0;
		currentState.matchedNodeCount = 0;
		currentState.comparedNodeCount = 0;
		currentState.fail = false;
		currentState.mismatch = false;
		currentState.reachedTargetDepth = false;
		currentState.hasAmbiguity = false;
		currentState.complete = true;
		stateIndex = 0;

		comparedNodeSet.clear();
		comparedNodeList.clear();
		
		this.leftSubtreeRoot = leftSubtreeRoot;
		this.rightSubtreeRoot = rightSubtreeRoot;
	}

	public void fail() {
		if ((leftSubtreeRoot.getKey().equals(rightSubtreeRoot.getKey())) && (stateIndex == 0))
			currentState.mismatch = true; // could store location of mismatch by taking current L/R pair as args
		
		currentState.fail = true;
	}

	public boolean isFailed() {
		return currentState.fail;
	}

	public void addComparedNode(Node<?> node) {
		comparedNodeSet.add(node);
		comparedNodeList.add(node);
	}

	public boolean isAlreadyCompared(Node<?> node) {
		return comparedNodeSet.contains(node);
	}

	public void saveState() {
		if (stateIndex == stateStack.size())
			stateStack.add(new State());
		currentState.comparedNodeCount = comparedNodeList.size();
		currentState.copyTo(stateStack.get(stateIndex++));
	}

	public void rewindState() {
		stateStack.get(--stateIndex).copyTo(currentState);

		while (comparedNodeList.size() > currentState.comparedNodeCount) {
			comparedNodeSet.remove(comparedNodeList.remove(comparedNodeList.size() - 1));
		}
	}

	public void commitState() {
		stateIndex--;
	}

	public void addEdge(int depth, EdgeMatchType type) {
		if (currentState.index == edges.size())
			edges.add(new EdgeComparison());
		EdgeComparison comparison = edges.get(currentState.index++);
		comparison.depth = depth;
		comparison.type = type;

		if (depth == 0) {
			currentState.reachedTargetDepth = true;
		}
		switch (type) {
			case DIRECT_MATCH:
			case INDIRECT_UNIQUE_MATCH:
				currentState.matchedNodeCount++;
				break;
			case INDIRECT_AMBIGUOUS_MATCH:
				currentState.matchedNodeCount++;
				//$FALL-THROUGH$
			case INDIRECT_DIVERGENCE:
				currentState.hasAmbiguity = true;
				//$FALL-THROUGH$
			case ONE_SIDE_ONLY:
				currentState.complete = true;
		}
	}

	int evaluate() {
		if (currentState.fail)
			return -1;

		if (currentState.complete) {
			if (currentState.reachedTargetDepth)
				return 10000;
			else if (currentState.reachedTargetDepth)
				return 1000;
			else
				return currentState.matchedNodeCount * 3;
		}

		if (currentState.reachedTargetDepth) {
			if (currentState.hasAmbiguity)
				return currentState.matchedNodeCount * 2;
			else
				return currentState.matchedNodeCount * 10;
		} else {
			if (currentState.hasAmbiguity)
				return currentState.matchedNodeCount / 2;
			else
				return currentState.matchedNodeCount;
		}
	}
}
