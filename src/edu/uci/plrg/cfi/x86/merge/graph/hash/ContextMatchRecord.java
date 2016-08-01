package edu.uci.plrg.cfi.x86.merge.graph.hash;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.plrg.cfi.x86.graph.data.graph.Node;

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

	private static final int INITIAL_COMPARISON_COUNT = 100;
	private static final int INITIAL_STATE_COUNT = 20;

	private final ContextMatchState currentState = new ContextMatchState();
	private HashMergeSession.MergeEvaluator evaluator;

	private final List<EdgeComparison> edges = new ArrayList<EdgeComparison>(INITIAL_COMPARISON_COUNT);

	// In case of recursively compute the similarity of cyclic graph, record
	// the compared nodes every time getContextSimilarity is called
	private final Set<Node<?>> comparedNodeSet = new HashSet<Node<?>>();
	private final List<Node<?>> comparedNodeList = new ArrayList<Node<?>>();

	private int stateIndex;
	private final List<ContextMatchState> stateStack = new ArrayList<ContextMatchState>();

	private Node<?> leftSubtreeRoot;
	private Node<?> rightSubtreeRoot;

	public ContextMatchRecord() {
		this(new HashMergeSession.DefaultEvaluator());
	}

	public ContextMatchRecord(HashMergeSession.MergeEvaluator evaluator) {
		this.evaluator = evaluator;

		for (int i = 0; i < INITIAL_COMPARISON_COUNT; i++) {
			edges.add(new EdgeComparison());
		}
		for (int i = 0; i < INITIAL_STATE_COUNT; i++) {
			stateStack.add(new ContextMatchState());
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

	void setEvaluator(HashMergeSession.MergeEvaluator evaluator) {
		this.evaluator = evaluator;
	}

	public void fail(String format, Object... args) {
		if ((leftSubtreeRoot.getKey().equals(rightSubtreeRoot.getKey())) && (stateIndex == 0))
			currentState.mismatch = true; // could store location of mismatch by taking current L/R pair as args

		// Log.log(String.format("Fail at level %d: %s", stateIndex, format), args);

		currentState.fail = true;
	}

	public boolean isFailed() {
		return ((stateIndex == 0) && currentState.fail);
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
			stateStack.add(new ContextMatchState());
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

		return evaluator.evaluateMatch(currentState);
	}
}
