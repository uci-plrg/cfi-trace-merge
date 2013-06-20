package analysis.graph.representation;

import java.util.ArrayList;
import java.util.HashMap;

import analysis.graph.debug.DebugUtils;
import analysis.graph.representation.SpeculativeScoreRecord.MatchResult;
import analysis.graph.representation.SpeculativeScoreRecord.SpeculativeScoreType;

public class SpeculativeScoreList {
	private ArrayList<SpeculativeScoreRecord> records;

	private int[] indirectScoreCaseCnts;
	private int[] pureHeuristicsScoreCaseCnts;
	private int[] matchResultCnts;

	HashMap<MatchResult, ArrayList<SpeculativeScoreRecord>> result2Records;

	public SpeculativeScoreList() {
		records = new ArrayList<SpeculativeScoreRecord>();
		indirectScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		pureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		matchResultCnts = new int[MatchResult.values().length];

		for (int i = 0; i < matchResultCnts.length; i++) {
			matchResultCnts[i] = 0;
		}

		result2Records = new HashMap<MatchResult, ArrayList<SpeculativeScoreRecord>>();
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

		if (!result2Records.containsKey(res)) {
			result2Records.put(res, new ArrayList<SpeculativeScoreRecord>());
		}
		ArrayList<SpeculativeScoreRecord> records = result2Records.get(res);
		records.add(record);
	}

	public void count() {
		// Can do something here

	}

	public void showResult() {
		System.out
				.println("The statistical results for speculative scoring is:");

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			System.out.println("Total indirect "
					+ SpeculativeScoreType.values()[i] + ": "
					+ indirectScoreCaseCnts[i]);
		}
		System.out.println();

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			System.out.println("Total pure heuristics "
					+ SpeculativeScoreType.values()[i] + ": "
					+ pureHeuristicsScoreCaseCnts[i]);
		}
		System.out.println();

		// Output overall number of incorrect matches and its detailed records
		for (int i = 0; i < MatchResult.values().length; i++) {
			MatchResult res = MatchResult.values()[i];
			System.out.println("Total " + res + ": " + matchResultCnts[i]);

			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				// if (false) {
				// Only output the details of the mismatches and the unknown
				if (res.toString().indexOf("Match") == -1) {
					ArrayList<SpeculativeScoreRecord> records = result2Records
							.get(res);
					if (records == null) {
						continue;
					}
					for (int j = 0; j < records.size(); j++) {
						System.out.println(records.get(j));
					}
				}
			}
		}
	}
}
