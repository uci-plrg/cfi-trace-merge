package analysis.graph.representation;

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
	// All the above cases include both indirect speculation and pure speculation
	public static enum MatchResult {
		IndirectExistingCorrectMatch,
		IndirectExistingMismatch,
		IndirectExistingUnfoundMismatch,
		IndirectNonExistingMismatch,
		IndirectNonExistingCorrectMatch,

		PureHeuristicsExistingCorrectMatch,
		PureHeuristicsExistingMismatch,
		PureHeuristicsExistingUnfoundMismatch,
		PureHeuristicsNonExistingMismatch,
		PureHeuristicsNonExistingCorrectMatch,
	}

	public final SpeculativeScoreType speculativeScoreType;
	public final boolean isIndirectSpeculation;

	public final int matchingScore;

	// Record if this speculative matching is correct or not
	public final MatchResult matchResult;

	public String toString() {
		if (isIndirectSpeculation) {
			return "Indirect: " + speculativeScoreType + "-" + matchingScore;
		} else {
			return "PureHeuristics: " + speculativeScoreType + "-"
					+ matchingScore;
		}
	}

	public SpeculativeScoreRecord(SpeculativeScoreType speculativeScoreType,
			boolean isIndirectSpeculation, int matchingScore,
			MatchResult matchResult) {
		this.speculativeScoreType = speculativeScoreType;
		this.isIndirectSpeculation = isIndirectSpeculation;
		this.matchingScore = matchingScore;
		this.matchResult = matchResult;
	}
}
