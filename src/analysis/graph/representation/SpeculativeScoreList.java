package analysis.graph.representation;

import java.util.ArrayList;

import analysis.graph.representation.SpeculativeScoreRecord.MatchResult;
import analysis.graph.representation.SpeculativeScoreRecord.SpeculativeScoreType;

public class SpeculativeScoreList {
	private ArrayList<SpeculativeScoreRecord> records;

	private int[] indirectScoreCaseCnts;
	private int[] pureHeuristicsScoreCaseCnts;
	private int[] matchResultCnts;

	public SpeculativeScoreList() {
		records = new ArrayList<SpeculativeScoreRecord>();
		indirectScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		pureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		matchResultCnts = new int[MatchResult.values().length];
		
		for (int i = 0; i < matchResultCnts.length; i++) {
			matchResultCnts[i] = 0;
		}
	}

	public void add(SpeculativeScoreRecord record) {
		records.add(record);
		
		if (record.isIndirectSpeculation) {
			indirectScoreCaseCnts[record.speculativeScoreType.ordinal()]++;
		} else {
			pureHeuristicsScoreCaseCnts[record.speculativeScoreType.ordinal()]++;
		}
	
		// Update the count of the corresponding case 
		MatchResult res = record.matchResult;
		matchResultCnts[res.ordinal()]++;
	}

	public void showResult() {
		System.out
				.println("The statistical results for speculative scoring is:");
		
		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			System.out
					.println("Total indirect " + SpeculativeScoreType.values()[i] + ": "
							+ indirectScoreCaseCnts[i]);
		}
		System.out.println();
		
		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			System.out
					.println("Total pure heuristics " + SpeculativeScoreType.values()[i] + ": "
							+ pureHeuristicsScoreCaseCnts[i]);
		}
		System.out.println();

		// Output overall number of incorrect matches
		for (int i = 0; i < MatchResult.values().length; i++) {
			System.out
					.println("Total " + MatchResult.values()[i] + ": "
							+ matchResultCnts[i]);
		}
	}
}
