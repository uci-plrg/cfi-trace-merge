package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleDescriptor;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.SpeculativeScoreRecord.SpeculativeScoreType;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;

public class SpeculativeScoreList {

	public static void showGlobalStats() {
		int matchCnt = 0;

		int[] globalIndirectScoreCaseCnts = new int[SpeculativeScoreType
				.values().length];
		int[] globalPureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType
				.values().length];
		int[] globalMatchResultCnts = new int[MatchResult.values().length];
		for (int i = 0; i < globalIndirectScoreCaseCnts.length; i++) {
			globalIndirectScoreCaseCnts[i] = 0;
			globalPureHeuristicsScoreCaseCnts[i] = 0;
		}
		for (int i = 0; i < globalMatchResultCnts.length; i++) {
			globalMatchResultCnts[i] = 0;
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

	private final GraphMergeSession session;
	private final List<SpeculativeScoreRecord> records = new ArrayList<SpeculativeScoreRecord>();
	private final Map<MatchResult, ArrayList<SpeculativeScoreRecord>> result2Records = new HashMap<MatchResult, ArrayList<SpeculativeScoreRecord>>();

	private int[] indirectScoreCaseCnts = new int[SpeculativeScoreType.values().length];
	private int[] pureHeuristicsScoreCaseCnts = new int[SpeculativeScoreType
			.values().length];
	private int[] matchResultCnts = new int[MatchResult.values().length];

	private boolean hasConflict;

	public SpeculativeScoreList(GraphMergeSession session) {
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

	public void add(SpeculativeScoreRecord record) {
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
							int score = session.engine
									.debug_getContextSimilarity(trueNode1,
											node2, depth);
							// System.out.println("Score: " + score + " -- "
							// + depth);
							if (score == -1) {
								System.out.println("bad!");
							} else if (score == 1) {
								continue;
							}

						}
					}
				}
			}
		}
	}
}
