package edu.uci.eecs.crowdsafe.analysis.data.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

public class SpeculativeScoreRecord {
	/**
	 * SpeculativeMatchingType enumerates the possible cases where speculative
	 * matches are made. The boundary for low score is hypothetically defined as
	 * 10
	 * 
	 * @author peizhaoo
	 * 
	 */
	public static int LowScore = 10;

	public static enum SpeculativeScoreType {
		// A low score because of reaching the tail of the program
		LowScoreTail,

		// A low score because of a divergence of the node (lack of info)
		LowScoreDivergence,

		// Only one possible matching, but it turns out to be a false one
		OneMatchFalse,

		// Only one possible matching, and it is a true one
		OneMatchTrue,

		// Many possible matches, and it makes the speculation ambiguous
		ManyMatchesAmbiguity,

		// Many possible matches, but without ambiguous speculation
		ManyMatchesCorrect,

		// No possible candidate match
		NoMatch
	}

	// 1. If both tags exist in both execution:
	// a. find a correct one ---- correct match
	// b. find a wrong one ---- mismatch
	// c. can't find it ---- unfound mismatch
	// 2. If the corresponding tag does not exist in execution 1:
	// a. find it ---- non-existing mismatch
	// b. can't find it ---- correct match
	// 3. If the module is not known, we record the result as unknown
	// All the above cases include both indirect speculation and pure
	// speculation
	public static enum MatchResult {
		IndirectExistingCorrectMatch, IndirectExistingMismatch, IndirectExistingUnfoundMismatch, IndirectNonExistingMismatch, IndirectNonExistingCorrectMatch,

		PureHeuristicsExistingCorrectMatch, PureHeuristicsExistingMismatch, PureHeuristicsExistingUnfoundMismatch, PureHeuristicsNonExistingMismatch, PureHeuristicsNonExistingCorrectMatch,

		Unknown
	}

	public final SpeculativeScoreType speculativeScoreType;
	public final boolean isIndirectSpeculation;

	public final int matchingScore;

	// Record if this speculative matching is correct or not
	public final MatchResult matchResult;

	// Record the matched nodes, node1 can be null
	public final ExecutionNode node1, node2;

	// The actual chosen node1
	public final ExecutionNode selectedNode1;

	public String toString() {
		String node1IdxStr = node1 == null ? "null" : Integer.toString(node1
				.getIndex()), actualNode1IdxStr = selectedNode1 == null ? "null"
				: Integer.toString(selectedNode1.getIndex());

		if (isIndirectSpeculation) {
			return "Indirect: " + speculativeScoreType + " -- ("
					+ matchingScore + ") " + actualNode1IdxStr + "<=>"
					+ node2.getIndex() + " [" + node1IdxStr + "] -- "
					+ matchResult;
		} else {
			return "PureHeuristics: " + speculativeScoreType + " -- ("
					+ matchingScore + ") " + actualNode1IdxStr + "<=>"
					+ node2.getIndex() + " [" + node1IdxStr + "] -- "
					+ matchResult;
		}
	}

	public SpeculativeScoreRecord(SpeculativeScoreType speculativeScoreType,
			boolean isIndirectSpeculation, int matchingScore, ExecutionNode node1,
			ExecutionNode node2, ExecutionNode selectedNode1, MatchResult matchResult) {
		this.speculativeScoreType = speculativeScoreType;
		this.isIndirectSpeculation = isIndirectSpeculation;
		this.matchingScore = matchingScore;
		this.node1 = node1;
		this.node2 = node2;
		this.selectedNode1 = selectedNode1;
		this.matchResult = matchResult;
	}
}
