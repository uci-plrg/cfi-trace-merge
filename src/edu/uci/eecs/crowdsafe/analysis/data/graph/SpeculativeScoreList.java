package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;
import java.util.HashMap;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.analysis.data.graph.SpeculativeScoreRecord.SpeculativeScoreType;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.GraphMerger;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;

import utils.AnalysisUtil;

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
		// Currently don't do this. The graph is too big and it will lead to
		// out-of-memory error
		// SpeculativeScoreList.allLists.add(this);
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
						Node selectedNode1 = record.selectedNode1;
						Node node2 = record.node2;
						long node2RelativeTag = node2.getRelativeTag();
						int node2Version = node2.getTagVersion();
						SoftwareDistributionUnit node2Unit = node2.getModule().unit;
						ModuleDescriptor node1Module = graphMerger.getGraph1()
								.getModules().getModule(node2Unit);
						long node1Tag = node1Module.beginAddr
								+ node2RelativeTag;
						Node trueNode1 = graphMerger.getGraph1()
								.getModuleGraphCluster(node2Unit)
								.getModuleGraph(node2Unit).getGraphData()
								.getNode(new Node.Key(node1Tag, node2Version));
						if (trueNode1 != null) {
							// int depth = (int) (graphMerger.getGraph1()
							// .getNodes().size() * 0.1f);
							int depth = 200;
							if (DebugUtils.debug) {
								graphMerger.comparedNodes.clear();
							}
							int score = graphMerger.debug_getContextSimilarity(
									trueNode1, node2, depth);
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
