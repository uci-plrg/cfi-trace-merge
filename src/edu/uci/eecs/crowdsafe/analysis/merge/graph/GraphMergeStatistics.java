package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.analysis.util.AnalysisUtil;
import edu.uci.eecs.crowdsafe.util.log.Log;

public class GraphMergeStatistics {

	public static final float LOW_MATCHING_TRHESHOLD = 0.15f;

	private final GraphMergeSession session;

	private int directEdgeTrialCnt = 0;
	private int indirectEdgeTrialCnt = 0;
	private int indirectEdgeMatchCnt = 0;
	private int pureHeuristicTrialCnt = 0;
	private int pureHeuristicMatchCnt = 0;
	private int directMatchCnt = 0;
	private int callContinuationMatchCnt = 0;

	private float setInterRate = 0f;
	private int totalNodeSize = 0;
	private int totalHashSize = 0;
	private int interHashSize = 0;

	public GraphMergeStatistics(GraphMergeSession session) {
		this.session = session;

		Set<Long> interBlockHashes = AnalysisUtil.intersection(
				session.left.cluster.getGraphData().nodesByHash.keySet(),
				session.right.cluster.getGraphData().nodesByHash.keySet());
		Set<Long> totalBlockHashes = AnalysisUtil.union(
				session.left.cluster.getGraphData().nodesByHash.keySet(),
				session.right.cluster.getGraphData().nodesByHash.keySet());
		interHashSize = interBlockHashes.size();
		totalHashSize = totalBlockHashes.size();
		setInterRate = (float) interBlockHashes.size() / totalHashSize;
	}

	public void reset() {
		directEdgeTrialCnt = 0;
		indirectEdgeTrialCnt = 0;
		indirectEdgeMatchCnt = 0;
		pureHeuristicTrialCnt = 0;
		pureHeuristicMatchCnt = 0;
		directMatchCnt = 0;
		callContinuationMatchCnt = 0;

		setInterRate = 0f;
		totalNodeSize = 0;
		totalHashSize = 0;
		interHashSize = 0;
	}

	public void tryDirectMatch() {
		directEdgeTrialCnt++;
	}

	public void tryIndirectMatch() {
		indirectEdgeTrialCnt++;
	}

	public void tryPureHeuristicMatch() {
		pureHeuristicTrialCnt++;
	}

	public void indirectMatch() {
		indirectEdgeMatchCnt++;
	}

	public void pureHeuristicMatch() {
		pureHeuristicMatchCnt++;
	}

	public void directMatch() {
		directMatchCnt++;
	}

	public void callContinuationMatch() {
		callContinuationMatchCnt++;
	}

	public ArrayList<ExecutionNode> unmatchedGraph1Nodes() {
		ArrayList<ExecutionNode> unmatchedNodes = new ArrayList<ExecutionNode>();
		for (int i = 0; i < session.left.cluster.getGraphData().nodes.size(); i++) {
			ExecutionNode n = session.left.cluster.getGraphData().nodes.get(i);
			if (!session.matchedNodes.containsLeftKey(n.getKey())) {
				unmatchedNodes.add(n);
			}
		}
		return unmatchedNodes;
	}

	public ArrayList<ExecutionNode> unmatchedGraph2Nodes() {
		ArrayList<ExecutionNode> unmatchedNodes = new ArrayList<ExecutionNode>();
		for (int i = 0; i < session.right.cluster.getGraphData().nodes.size(); i++) {
			ExecutionNode n = session.right.cluster.getGraphData().nodes.get(i);
			if (!session.matchedNodes.containsRightKey(n.getKey())) {
				unmatchedNodes.add(n);
			}
		}
		return unmatchedNodes;
	}

	public boolean lowMatching() {
		float nodeInterRate = (float) session.matchedNodes.size()
				/ totalNodeSize;
		if ((setInterRate - nodeInterRate) > LOW_MATCHING_TRHESHOLD) {
			return true;
		} else {
			return false;
		}
	}

	public void dumpMatchedNodes() {
		for (Node.Key leftKey : session.matchedNodes.getLeftKeySet()) {
			Node.Key rightKey = session.matchedNodes.getMatchByLeftKey(leftKey);
			Log.log(leftKey + "<-->" + rightKey);
		}
	}

	synchronized public void outputMergedGraphInfo() {
		totalNodeSize = session.left.cluster.getGraphData().nodes.size()
				+ session.right.cluster.getGraphData().nodes.size()
				- session.matchedNodes.size();
		Log.log("Comparison between "
				+ session.left.cluster.getGraphData().containingGraph.dataSource
						.getProcessName()
				+ session.left.cluster.getGraphData().containingGraph.dataSource
						.getProcessId()
				+ " & "
				+ session.right.cluster.getGraphData().containingGraph.dataSource
						.getProcessName()
				+ session.right.cluster.getGraphData().containingGraph.dataSource
						.getProcessId() + ":");
		// TODO: print a comparison header
		// Log.log(AnalysisUtil.getRunStr(graph1.getRunDir())
		// + " & " + AnalysisUtil.getRunStr(graph2.getRunDir()));
		Log.log("Total block hashes of the whole graph1: "
				+ session.left.cluster.getGraphData().nodesByHash.keySet()
						.size());
		Log.log("Total block hashes of the whole graph2: "
				+ session.right.cluster.getGraphData().nodesByHash.keySet()
						.size());
		Set<Long> totalBlockSet = AnalysisUtil.intersection(
				session.left.cluster.getGraphData().nodesByHash.keySet(),
				session.right.cluster.getGraphData().nodesByHash.keySet());
		Log.log("Total block hashes: " + totalBlockSet.size());

		Log.log("Size of nodes in graph1: "
				+ session.left.cluster.getGraphData().nodes.size());
		Log.log("Size of nodes in graph2: "
				+ session.right.cluster.getGraphData().nodes.size());

		Log.log("Intersection ratio of block hashes: "
				+ setInterRate
				+ "  "
				+ session.left.cluster.getGraphData().nodesByHash.keySet()
						.size()
				+ ","
				+ session.right.cluster.getGraphData().nodesByHash.keySet()
						.size() + ":" + interHashSize + "/" + totalHashSize);
		Log.log("Graph1 nodes: "
				+ session.left.cluster.getGraphData().nodes.size());
		Log.log("Graph2 nodes: "
				+ session.right.cluster.getGraphData().nodes.size());
		Log.log("Total nodes: " + totalNodeSize);

		Log.log("Merged nodes / G1 nodes: "
				+ (float) session.matchedNodes.size()
				/ session.left.cluster.getGraphData().nodes.size());
		Log.log("Merged nodes / G2 nodes: "
				+ (float) session.matchedNodes.size()
				/ session.right.cluster.getGraphData().nodes.size());
		float nodeInterRate = (float) session.matchedNodes.size()
				/ totalNodeSize;
		Log.log("Merged nodes / all nodes: " + nodeInterRate);

		Log.log("Indirect edge matched: " + indirectEdgeMatchCnt);
		Log.log("Pure Heuristic match: " + pureHeuristicMatchCnt);
		Log.log("CallContinuation Match: " + callContinuationMatchCnt);

		Log.log();
	}

	public void dumpNodesRelationship(String fileName) {
		File file = new File(fileName);
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		PrintWriter pwRelationFile = null;

		try {
			pwRelationFile = new PrintWriter(fileName + ".relation");
			for (Node.Key leftKey : session.matchedNodes.getLeftKeySet()) {
				Node.Key rightKey = session.matchedNodes
						.getMatchByLeftKey(leftKey);
				pwRelationFile.println(leftKey + "->" + rightKey);
			}

			pwRelationFile.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (pwRelationFile != null)
			pwRelationFile.close();
	}

	public static void dumpGraph(ModuleGraphCluster graph, String fileName) {
		Log.log("Dump the graph for " + graph + " to " + fileName);
		File file = new File(fileName);
		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		file = new File(fileName + ".node");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		PrintWriter pwDotFile = null, pwNodeFile = null;

		try {
			pwDotFile = new PrintWriter(fileName);

			// This file contains the analysis info for the graph
			pwNodeFile = new PrintWriter(fileName + ".node");
			Set<ExecutionNode> accessibleNodes = graph.searchAccessibleNodes();
			for (int i = 0; i < graph.getGraphData().nodes.size(); i++) {
				ExecutionNode n = graph.getGraphData().nodes.get(i);
				if (!accessibleNodes.contains(n)) {
					pwNodeFile.println(n);
				}

			}

			pwDotFile.println("digraph runGraph {");
			for (int i = 0; i < graph.getGraphData().nodes.size(); i++) {
				ExecutionNode n = graph.getGraphData().nodes.get(i);
				pwDotFile.println(i + "[label=\"" + n + "\"]");

				List<Edge<ExecutionNode>> edges = graph.getGraphData().nodes
						.get(i).getOutgoingEdges();
				for (Edge<ExecutionNode> e : edges) {
					String branchType;
					switch (e.getEdgeType()) {
						case INDIRECT:
							branchType = "i";
							break;
						case DIRECT:
							branchType = "d";
							break;
						case CALL_CONTINUATION:
							branchType = "c";
							break;
						case UNEXPECTED_RETURN:
							branchType = "u";
							break;
						case CROSS_MODULE:
							branchType = "xk";
							break;
						case CROSS_CUSTOM_MODULE:
							branchType = "xc";
							break;
						default:
							branchType = "";
							break;
					}
					String edgeLabel = i + "->" + e.getToNode().getKey()
							+ "[label=\"" + branchType + "_";
					if (e.getEdgeType() == EdgeType.CROSS_MODULE) {
						edgeLabel = edgeLabel
								+ e.getFromNode().getRelativeTag() + "->"
								+ e.getToNode().getRelativeTag() + "\"]";
					} else {
						edgeLabel = edgeLabel + e.getOrdinal() + "\"]";
					}
					pwDotFile.println(edgeLabel);
				}
			}

			pwDotFile.print("}");
			pwDotFile.flush();

			pwNodeFile.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (pwDotFile != null)
			pwDotFile.close();
		if (pwNodeFile != null)
			pwNodeFile.close();
		Log.log("Finish dumping the graph for " + graph + ".");
	}

	public static void dumpHashCollision(ProcessExecutionGraph graph) {

	}

	public int getTotalNodeSize() {
		return totalNodeSize;
	}

	public float getSetInterRate() {
		return setInterRate;
	}
}
