package analysis.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;

import utils.AnalysisUtil;
import analysis.graph.representation.CompleteExecutionGraph;
import analysis.graph.representation.Edge;
import analysis.graph.representation.EdgeType;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.MatchedNodes;
import analysis.graph.representation.ModuleGraph;
import analysis.graph.representation.Node;
import analysis.graph.representation.NormalizedTag;

public class GraphMergingInfo {

	public final ExecutionGraph graph1, graph2;
	public final MatchedNodes matchedNodes;
	
	private int indirectEdgeTrialCnt = 0;
	private int indirectEdgeMatchCnt = 0;
	private int pureHeuristicTrialCnt = 0;
	private int pureHeuristicMatchCnt = 0;
	private int directMatchCnt = 0;
	private int callContinuationMatchCnt = 0;

	public static final float LowMatchingThreshold = 0.15f;

	private float setInterRate;
	private int totalNodeSize;
	private int totalHashSize;
	private int interHashSize;

	public GraphMergingInfo(ExecutionGraph graph1, ExecutionGraph graph2,
			MatchedNodes matchedNodes) {
		this.graph1 = graph1;
		this.graph2 = graph2;
		this.matchedNodes = matchedNodes;

		HashSet<Long> interBlockHashes = AnalysisUtil.intersection(
				graph1.getBlockHashes(), graph2.getBlockHashes());
		HashSet<Long> totalBlockHashes = AnalysisUtil.union(
				graph1.getBlockHashes(), graph2.getBlockHashes());
		interHashSize = interBlockHashes.size();
		totalHashSize = totalBlockHashes.size();
		setInterRate = (float) interBlockHashes.size() / totalHashSize;
		// totalNodeSize = graph1.getAccessibleNodes().size() +
		// graph2.getAccessibleNodes().size()
		// - matchedNodes.size();
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

	public ArrayList<Node> unmatchedGraph1Nodes() {
		ArrayList<Node> unmatchedNodes = new ArrayList<Node>();
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n = graph1.getNodes().get(i);
			if (!matchedNodes.containsKeyByFirstIndex(n.getIndex())) {
				unmatchedNodes.add(n);
			}
		}
		return unmatchedNodes;
	}

	public ArrayList<Node> unmatchedGraph2Nodes() {
		ArrayList<Node> unmatchedNodes = new ArrayList<Node>();
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node n = graph2.getNodes().get(i);
			if (!matchedNodes.containsKeyBySecondIndex(n.getIndex())) {
				unmatchedNodes.add(n);
			}
		}
		return unmatchedNodes;
	}

	public boolean lowMatching() {
		float nodeInterRate = (float) matchedNodes.size() / totalNodeSize;
		if ((setInterRate - nodeInterRate) > LowMatchingThreshold) {
			return true;
		} else {
			return false;
		}
	}

	public void dumpMatchedNodes() {
		for (int index1 : matchedNodes) {
			int index2 = matchedNodes.getByFirstIndex(index1);
			System.out.println(index1 + "<-->" + index2);
		}
	}

	synchronized public void outputMergedGraphInfo() {
		totalNodeSize = graph1.getNodes().size() + graph2.getNodes().size()
				- matchedNodes.size();
		if (graph1 instanceof ModuleGraph) {
			System.out.println("Comparison for " + graph1.getProgName() + "_"
					+ graph1.getPid() + " & " + graph2.getProgName() + "_"
					+ graph2.getPid() + ":");
		} else {
			System.out.println("Comparison between " + graph1.getProgName()
					+ graph1.getPid() + " & " + graph2.getProgName()
					+ graph2.getPid() + ":");
			System.out.println(AnalysisUtil.getRunStr(graph1.getRunDir())
					+ " & " + AnalysisUtil.getRunStr(graph2.getRunDir()));

			System.out.println("Size of nodes in graph1: "
					+ graph1.getNodes().size());
			System.out.println("Size of nodes in graph2: "
					+ graph2.getNodes().size());
			
			HashSet<Long> totalBlockSet = AnalysisUtil.intersection(graph1.getTotalBlockHashes(), graph2.getTotalBlockHashes());
			System.out.println("Total block hashes: " + totalBlockSet.size());
			System.out.println("Total block hashes of graph1: " + graph1.getTotalBlockHashes().size());
			System.out.println("Total block hashes of graph2: " + graph2.getTotalBlockHashes().size());
		}

		System.out.println("Intersection ratio of block hashes: "
				+ setInterRate + "  " + graph1.getBlockHashes().size() + ","
				+ graph2.getBlockHashes().size() + ":" + interHashSize + "/"
				+ totalHashSize);
		System.out.println("Merged nodes: " + matchedNodes.size());
		System.out.println("Graph1 nodes: " + graph1.getNodes().size());
		System.out.println("Graph2 nodes: " + graph2.getNodes().size());
		System.out.println("Total nodes: " + totalNodeSize);

		System.out.println("Merged nodes / G1 nodes: "
				+ (float) matchedNodes.size() / graph1.getNodes().size());
		System.out.println("Merged nodes / G2 nodes: "
				+ (float) matchedNodes.size() / graph2.getNodes().size());
		float nodeInterRate = (float) matchedNodes.size() / totalNodeSize;
		System.out.println("Merged nodes / all nodes: " + nodeInterRate);
		
		System.out.println("Indirect edge trial: " + indirectEdgeTrialCnt);
		System.out.println("Indirect edge matched: " + indirectEdgeMatchCnt);
		System.out.println("Pure Heuristic trial: " + pureHeuristicTrialCnt);
		System.out.println("Pure Heuristic match: " + pureHeuristicMatchCnt);
		System.out.println("Direct match: " + directMatchCnt);
		System.out.println("CallContinuation Match: " + callContinuationMatchCnt);
		
		System.out.println();
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
			for (int index1 : matchedNodes) {
				int index2 = matchedNodes.getByFirstIndex(index1);
				pwRelationFile.println(index1 + "->" + index2);
			}

			pwRelationFile.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (pwRelationFile != null)
			pwRelationFile.close();
	}

	public static long outputFirstMain(ExecutionGraph graph) {
		Node n = null;
		long firstMainHash = -1;
		for (int i = 0; i < graph.getNodes().size(); i++) {
			if (graph.getNodes().get(i).getHash() == GraphMerger.specialHash) {
				n = graph.getNodes().get(i);
				if (n.getOutgoingEdges().size() > 1) {
					for (int j = 0; j < n.getOutgoingEdges().size(); j++) {
						if (n.getOutgoingEdges().get(j).getEdgeType() == EdgeType.Indirect) {
							return n.getOutgoingEdges().get(j).getToNode()
									.getHash();
						}
					}
					System.out.println("More than one target!");
					return n.getOutgoingEdges().size();
				} else {
					firstMainHash = n.getOutgoingEdges().get(0).getToNode()
							.getHash();
				}
				break;
			}
		}
		return firstMainHash;
	}

	public static void dumpGraph(ExecutionGraph graph, String fileName) {
		System.out.println("Dump the graph for " + graph + " to " + fileName);
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
			if (!(graph instanceof CompleteExecutionGraph)) {
				HashSet<Node> accessibleNodes = graph.getAccessibleNodes();
				for (int i = 0; i < graph.getNodes().size(); i++) {
					Node n = graph.getNodes().get(i);
					if (!accessibleNodes.contains(n)) {
						pwNodeFile.println(n);
					}

				}
			}

			pwDotFile.println("digraph runGraph {");
			long firstMainBlock = outputFirstMain(graph);
			pwDotFile.println("# First main block: "
					+ Long.toHexString(firstMainBlock));
			for (int i = 0; i < graph.getNodes().size(); i++) {
				Node n = graph.getNodes().get(i);
				pwDotFile.println(i + "[label=\"" + n + "\"]");

				ArrayList<Edge> edges = graph.getNodes().get(i)
						.getOutgoingEdges();
				for (Edge e : edges) {
					String branchType;
					switch (e.getEdgeType()) {
					case Indirect:
						branchType = "i";
						break;
					case Direct:
						branchType = "d";
						break;
					case CallContinuation:
						branchType = "c";
						break;
					case UnexpectedReturn:
						branchType = "u";
						break;
					case CrossKernelModule:
						branchType = "xk";
						break;
					case CrossCustomModule:
						branchType = "xc";
						break;
					default:
						branchType = "";
						break;
					}
					String edgeLabel = i + "->" + e.getToNode().getIndex()
							+ "[label=\"" + branchType + "_";
					if (e.getEdgeType() == EdgeType.CrossKernelModule) {
						edgeLabel = edgeLabel
								+ e.getFromNode().getNormalizedTag() + "->"
								+ e.getToNode().getNormalizedTag() + "\"]";
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
		System.out.println("Finish dumping the graph for " + graph + ".");
	}

	public static void dumpHashCollision(ExecutionGraph graph) {

	}

	public int getTotalNodeSize() {
		return totalNodeSize;
	}

	public float getSetInterRate() {
		return setInterRate;
	}
}
