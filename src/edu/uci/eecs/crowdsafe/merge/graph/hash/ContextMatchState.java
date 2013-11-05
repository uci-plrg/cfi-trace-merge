package edu.uci.eecs.crowdsafe.merge.graph.hash;

public class ContextMatchState {

	public interface Evaluator {
		int evaluateMatch(ContextMatchState state);
	}

	int index;
	int matchedNodeCount;
	int comparedNodeCount;
	boolean fail;
	boolean mismatch;
	boolean reachedTargetDepth;
	boolean hasAmbiguity;
	boolean complete;

	void copyTo(ContextMatchState target) {
		target.index = index;
		target.matchedNodeCount = matchedNodeCount;
		target.comparedNodeCount = comparedNodeCount;
		target.reachedTargetDepth = reachedTargetDepth;
		target.hasAmbiguity = hasAmbiguity;
		target.complete = complete;
		target.fail = false;
	}
	
	public boolean isFailed() {
		return fail;
	}
	
	public int getMatchedNodeCount() {
		return matchedNodeCount;
	}

	public int getComparedNodeCount() {
		return comparedNodeCount;
	}

	public boolean isMismatch() {
		return mismatch;
	}

	public boolean isReachedTargetDepth() {
		return reachedTargetDepth;
	}

	public boolean isHasAmbiguity() {
		return hasAmbiguity;
	}

	public boolean isComplete() {
		return complete;
	}
}
