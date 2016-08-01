package edu.uci.plrg.cfi.x86.merge.graph.hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.merge.graph.hash.HashSpeculationScoreRecord.MatchResult;
import edu.uci.plrg.cfi.x86.merge.graph.hash.HashSpeculationScoreRecord.SpeculativeScoreType;

public class HashSpeculationScoreList {

	public static void showGlobalStats() {
		int matchCnt = 0;

		int[] globalIndirectScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		int[] globalPureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType.values().length];
		int[] globalMatchResultCnts = new int[MatchResult.values().length];
		for (int i = 0; i < globalIndirectScoreCaseCnts.length; i++) {
			globalIndirectScoreCaseCnts[i] = 0;
			globalPureHeuristicsScoreCaseCnts[i] = 0;
		}
		for (int i = 0; i < globalMatchResultCnts.length; i++) {
			globalMatchResultCnts[i] = 0;
		}

		Log.log("The global statistical results for speculative scoring is:");

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			Log.log("Total indirect " + SpeculativeScoreType.values()[i] + ": "
					+ (float) globalIndirectScoreCaseCnts[i] / matchCnt);
		}
		Log.log();

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			Log.log("Total pure heuristics " + SpeculativeScoreType.values()[i] + ": "
					+ (float) globalPureHeuristicsScoreCaseCnts[i] / matchCnt);
		}
		Log.log();
		for (int i = 0; i < MatchResult.values().length; i++) {
			MatchResult res = MatchResult.values()[i];
			Log.log("Total " + res + ": " + globalMatchResultCnts[i]);
		}
	}

	private final HashMergeSession session;
	private final List<HashSpeculationScoreRecord> records = new ArrayList<HashSpeculationScoreRecord>();
	private final Map<MatchResult, ArrayList<HashSpeculationScoreRecord>> result2Records = new HashMap<MatchResult, ArrayList<HashSpeculationScoreRecord>>();

	private int[] indirectScoreCaseCnts = new int[SpeculativeScoreType.values().length];
	private int[] pureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType.values().length];
	private int[] matchResultCnts = new int[MatchResult.values().length];

	private boolean hasConflict;

	public HashSpeculationScoreList(HashMergeSession session) {
		this.session = session;
	}

	public void clear() {
		records.clear();
		result2Records.clear();

		for (int i = 0; i < indirectScoreCaseCnts.length; i++) {
			indirectScoreCaseCnts[i] = 0;
		}
		for (int i = 0; i < pureHeuristicsScoreCaseCnts.length; i++) {
			pureHeuristicsScoreCaseCnts[i] = 0;
		}
		for (int i = 0; i < matchResultCnts.length; i++) {
			matchResultCnts[i] = 0;
		}
	}

	public void setHasConflict(boolean hasConflict) {
		this.hasConflict = hasConflict;
	}

	public void add(HashSpeculationScoreRecord record) {
		// FIXME: Ad-hoc... Delete it when finishing analyzing.
		if (record.matchResult.toString().indexOf("found") == -1) {
			return;
		}

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
			result2Records.put(res, new ArrayList<HashSpeculationScoreRecord>());
		}
		ArrayList<HashSpeculationScoreRecord> records = result2Records.get(res);
		records.add(record);
	}

	public void count() {
		// Can do something here

	}

	public void showResult() {
		Log.log("The statistical results for speculative scoring is:");

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			Log.log("Total indirect " + SpeculativeScoreType.values()[i] + ": " + indirectScoreCaseCnts[i]);
		}
		Log.log();

		for (int i = 0; i < SpeculativeScoreType.values().length; i++) {
			Log.log("Total pure heuristics " + SpeculativeScoreType.values()[i] + ": " + pureHeuristicsScoreCaseCnts[i]);
		}
		Log.log();

		/**
		 * <pre>
		// Output overall number of incorrect matches and its detailed records
		for (int i = 0; i < MatchResult.values().length; i++) {
			MatchResult res = MatchResult.values()[i];
			Log.log("Total " + res + ": " + matchResultCnts[i]);

			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				// if (false) {
				// Only output the details of the mismatches and the unknown
				if (res.toString().indexOf("found") != -1) {
					ArrayList<SpeculativeScoreRecord> records = result2Records
							.get(res);
					if (records == null) {
						continue;
					}
					for (int j = 0; j < records.size(); j++) {
						SpeculativeScoreRecord record = records.get(j);
						ExecutionNode node2 = (ExecutionNode) record.rightNode;
						ExecutionNode trueNode1 = session.left.cluster
								.getGraphData().HACK_relativeTagLookup(node2);
						if (trueNode1 != null) {
							// int depth = (int) (graphMerger.getGraph1()
							// .getNodes().size() * 0.1f);
							int depth = 200;
							if (DebugUtils.debug) {
								session.comparedNodes.clear();
							}
							int score = session.engine.matcher
									.debug_getContextSimilarity(trueNode1,
											node2, depth);
							// Log.log("Score: " + score + " -- "
							// + depth);
							if (score == -1) {
								Log.log("bad!");
							} else if (score == 1) {
								continue;
							}

						}
					}
				}
			}
		}
		 */
	}
}
