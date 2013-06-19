package analysis.graph.representation;

import java.util.ArrayList;

import analysis.graph.representation.SpeculativeScoreRecord.MatchResult;
import analysis.graph.representation.SpeculativeScoreRecord.SpeculativeScoreType;

public class SpeculativeScoreList {
	private ArrayList<SpeculativeScoreRecord> records;

	private int[] indirectScoreCases;
	private int[] pureHeuristicsScoreCases;
	private int[] matchResultCnts;

	public SpeculativeScoreList() {
		records = new ArrayList<SpeculativeScoreRecord>();
		indirectScoreCases = new int[SpeculativeScoreType.values().length];
		pureHeuristicsScoreCases = new int[SpeculativeScoreType.values().length];
		matchResultCnts = new int[MatchResult.values().length];
	}

	public void add(SpeculativeScoreRecord record) {
		records.add(record);
	}

	public void showResult() {
		System.out
				.println("The statistical results for speculative scoring is:");

		// Indirect edge cases
		System.out.println("lowScoreTailIndirectCnt: "
				+ indirectScoreCases[SpeculativeScoreType.LowScoreTail
						.ordinal()]);

		System.out.println("lowScoreDivergenceIndirectCnt: "
				+ indirectScoreCases[SpeculativeScoreType.LowScoreDivergence
						.ordinal()]);
		System.out.println("oneMatchFalseIndirectCnt: "
				+ indirectScoreCases[SpeculativeScoreType.OneMatchFalse
						.ordinal()]);
		System.out.println("oneMatchTrueIndirectCnt: "
				+ indirectScoreCases[SpeculativeScoreType.OneMatchTrue
						.ordinal()]);
		System.out.println("manyMatchesAmbifuityIndirectCnt: "
				+ indirectScoreCases[SpeculativeScoreType.ManyMatchesAmbiguity
						.ordinal()]);
		System.out.println("manyMatchesCorrectIndirectCnt: "
				+ indirectScoreCases[SpeculativeScoreType.ManyMatchesCorrect
						.ordinal()]);

		System.out.println();

		// Pure heuristics cases
		System.out.println("lowScoreTailPureCnt: "
				+ pureHeuristicsScoreCases[SpeculativeScoreType.LowScoreTail
						.ordinal()]);
		System.out
				.println("lowScoreDivergencePureCnt: "
						+ pureHeuristicsScoreCases[SpeculativeScoreType.LowScoreDivergence
								.ordinal()]);
		System.out.println("oneMatchFalsePureCnt: "
				+ pureHeuristicsScoreCases[SpeculativeScoreType.OneMatchFalse
						.ordinal()]);
		System.out.println("oneMatchTruePureCnt: "
				+ pureHeuristicsScoreCases[SpeculativeScoreType.OneMatchTrue
						.ordinal()]);
		System.out
				.println("manyMatchesAmbifuityPureCnt: "
						+ pureHeuristicsScoreCases[SpeculativeScoreType.ManyMatchesAmbiguity
								.ordinal()]);
		System.out
				.println("manyMatchesCorrectPureCnt: "
						+ pureHeuristicsScoreCases[SpeculativeScoreType.ManyMatchesCorrect
								.ordinal()]);

		System.out.println();

		// Output overall number of incorrect matches
		System.out
				.println("Total indirect incorrect matches: "
						+ matchResultCnts[MatchResult.IndirectIncorrectMatch
								.ordinal()]);
		System.out.println("Total pure heuristics incorrect matches: "
				+ matchResultCnts[MatchResult.PureHeuristicsIncorrectMatch
						.ordinal()]);
	}

	public void count() {
		for (int i = 0; i < indirectScoreCases.length; i++) {
			indirectScoreCases[i] = 0;
			pureHeuristicsScoreCases[i] = 0;
		}
		for (int i = 0; i < matchResultCnts.length; i++) {
			matchResultCnts[i] = 0;
		}

		for (int i = 0; i < records.size(); i++) {
			SpeculativeScoreRecord record = records.get(i);
			matchResultCnts[record.matchResult.ordinal()]++;
			if (record.isIndirectSpeculation) {
				indirectScoreCases[record.speculativeScoreType.ordinal()]++;
			} else {
				pureHeuristicsScoreCases[record.speculativeScoreType.ordinal()]++;
			}
		}
	}
}
