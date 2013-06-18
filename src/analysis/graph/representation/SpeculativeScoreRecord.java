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
		ManyMatchesCorrect
	}

	public final SpeculativeScoreType speculativeScoreType;
	public final boolean isIndirectSpeculation;

	public SpeculativeScoreRecord(SpeculativeScoreType speculativeScoreType,
			boolean isIndirectSpeculation) {
		this.speculativeScoreType = speculativeScoreType;
		this.isIndirectSpeculation = isIndirectSpeculation;
	}
}
