package analysis.graph.representation;

import java.util.ArrayList;
import java.util.HashMap;

import analysis.graph.GraphMerger;
import analysis.graph.debug.DebugUtils;
import analysis.graph.representation.SpeculativeScoreRecord.MatchResult;
import analysis.graph.representation.SpeculativeScoreRecord.SpeculativeScoreType;

public class SpeculativeScoreList {
	// This is a global filed to record all the statistics during the comparison
	// Every time an instance is created, it should be added to allLists
	static public ArrayList<SpeculativeScoreList> allLists = new ArrayList<SpeculativeScoreList>();

	private GraphMerger graphMerger;
	private ArrayList<SpeculativeScoreRecord> records;
	private boolean hasConflict;

	private int[] indirectScoreCaseCnts;
	private int[] pureHeuristicsScoreCaseCnts;
	private int[] matchResultCnts;

	HashMap<MatchResult, ArrayList<SpeculativeScoreRecord>> result2Records;

	public void setHasConflict(boolean hasConflict) {
		this.hasConflict = hasConflict;
	}

	public SpeculativeScoreList(GraphMerger graphMerger) {
		this.graphMerger = graphMerger;
		
		records = new ArrayList<SpeculativeScoreRecord>();
		indirectScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		pureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		matchResultCnts = new int[MatchResult.values().length];

		for (int i = 0; i < matchResultCnts.length; i++) {
			matchResultCnts[i] = 0;
		}

		result2Records = new HashMap<MatchResult, ArrayList<SpeculativeScoreRecord>>();

		// Record all the history info
		SpeculativeScoreList.allLists.add(this);
	}

	public static void showGlobalStats() {
		int matchCnt = 0;

		int[] globalIndirectScoreCaseCnts = new int[SpeculativeScoreType
				.values().length];
		int[] globalPureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType
				.values().length];
		;
		int[] globalMatchResultCnts = new int[MatchResult.values().length];
		for (int i = 0; i < globalIndirectScoreCaseCnts.length; i++) {
			globalIndirectScoreCaseCnts[i] = 0;
			globalPureHeuristicsScoreCaseCnts[i] = 0;
		}
		for (int i = 0; i < globalMatchResultCnts.length; i++) {
			globalMatchResultCnts[i] = 0;
		}

		for (int i = 0; i < allLists.size(); i++) {
			SpeculativeScoreList list = allLists.get(i);
			if (!list.hasConflict) {
				matchCnt++;
				for (int j = 0; j < globalIndirectScoreCaseCnts.length; j++) {
					globalIndirectScoreCaseCnts[j] += list.indirectScoreCaseCnts[j];
					globalPureHeuristicsScoreCaseCnts[j] += list.pureHeuristicsScoreCaseCnts[j];
				}
				for (int j = 0; j < globalMatchResultCnts.length; j++) {
					globalMatchResultCnts[j] += list.matchResultCnts[j];
				}
			}
		}

		System.out
				.println("The global statistical results for speculative scoring is:");

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			System.out.println("Total indirect "
					+ SpeculativeScoreType.values()[i] + ": "
					+ (float) globalIndirectScoreCaseCnts[i] / matchCnt);
		}
		System.out.println();

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			System.out.println("Total pure heuristics "
					+ SpeculativeScoreType.values()[i] + ": "
					+ (float) globalPureHeuristicsScoreCaseCnts[i] / matchCnt);
		}
		System.out.println();
		for (int i = 0; i < MatchResult.values().length; i++) {
			MatchResult res = MatchResult.values()[i];
			System.out
					.println("Total " + res + ": " + globalMatchResultCnts[i]);
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
				if (res.toString().indexOf("Match") == -1
						&& res.toString().indexOf("found") == -1) {
					ArrayList<SpeculativeScoreRecord> records = result2Records
							.get(res);
					if (records == null) {
						continue;
					}
					for (int j = 0; j < records.size(); j++) {
						SpeculativeScoreRecord record = records.get(j);
						System.out.println(record);
						Node actualNode1 = record.actualNode1,
								node2 = record.node2;
						if (actualNode1 != null) {
							// Just to check if the mismatch will end up conflict
							int depth = (int) (graphMerger.getGraph1().getNodes().size() * 0.1f);
//							int depth = 200;
							
							if (DebugUtils.debug) {
								graphMerger.comparedNodes.clear();
							}
							
							int score = graphMerger.debug_getContextSimilarity(actualNode1, node2, depth);
							if (score == -1) {
								System.out.println("bad!");
							}
							System.out.println("Score: " + score + " -- " + depth);
						}
					}
				}
			}
		}
	}
}
