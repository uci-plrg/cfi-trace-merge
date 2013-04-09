package analysis.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import utils.AnalysisUtil;

import analysis.graph.representation.Edge;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.MatchedNodes;
import analysis.graph.representation.Node;

public class GraphInfo {
	public static void outputMergedGraphInfo(ExecutionGraph graph1,
			ExecutionGraph graph2, MatchedNodes matchedNodes) {
		System.out.println(graph1.getNodes().size());
		System.out.println(graph2.getNodes().size());

		HashSet<Long> interPairHashes = AnalysisUtil.intersection(
				graph1.getPairHashes(), graph2.getPairHashes()), interBlockHashes = AnalysisUtil
				.intersection(graph1.getBlockHashes(), graph2.getBlockHashes());
		HashSet<Long> totalPairHashes = new HashSet(graph1.getPairHashes());
		totalPairHashes.addAll(graph2.getPairHashes());
		HashSet<Long> totalBlockHashes = new HashSet(graph1.getBlockHashes());
		totalBlockHashes.addAll(graph2.getBlockHashes());
		// System.out.println("Intersection ratio of pair hashes: "
		// + (float) interPairHashes.size() / totalPairHashes.size());
		System.out.println("Intersection ratio of block hashes: "
				+ (float) interBlockHashes.size() / totalBlockHashes.size());
		int totalNodeSize = graph1.getNodes().size() + graph2.getNodes().size()
				- matchedNodes.size();
		System.out.println("Merged nodes: " + matchedNodes.size());
		System.out.println("Merged nodes / G1 nodes: "
				+ (float) matchedNodes.size() / graph1.getNodes().size());
		System.out.println("Merged nodes / G2 nodes: "
				+ (float) matchedNodes.size() / graph2.getNodes().size());
		System.out.println("Merged nodes / all nodes: "
				+ (float) matchedNodes.size() / totalNodeSize);
		System.out.println();
	}
	
	
	public static void dumpNodesRelationship(String fileName,
			HashMap<Integer, Integer> mergedNodes12) {
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
			for (int index1 : mergedNodes12.keySet()) {
				int index2 = mergedNodes12.get(index1);
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
				if (n.getEdges().size() > 1) {
					System.out.println("More than one target!");
					return n.getEdges().size();
				} else {
					firstMainHash = n.getEdges().get(0).getNode().getHash();
				}
				break;
			}
		}
		return firstMainHash;
	}
	
	public static void dumpGraph(ExecutionGraph graph, String fileName) {
		File file = new File(fileName);
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
			pwNodeFile = new PrintWriter(fileName + ".node");

			for (int i = 0; i < graph.getNodes().size(); i++) {
				pwNodeFile.println(Long.toHexString(graph.getNodes().get(i).getHash()));
			}

			pwDotFile.println("digraph runGraph {");
			long firstMainBlock = outputFirstMain(graph);
			pwDotFile.println("# First main block: "
					+ Long.toHexString(firstMainBlock));
			for (int i = 0; i < graph.getNodes().size(); i++) {
				// pw.println("node_" + Long.toHexString(nodes.get(i).hash));
				pwDotFile.println(i + "[label=\""
						+ Long.toHexString(graph.getNodes().get(i).getHash()) + "\"]");

				ArrayList<Edge> edges = graph.getNodes().get(i).getEdges();
				for (Edge e : edges) {
					String branchType;
					if (e.getIsDirect()) {
						branchType = "d";
					} else {
						branchType = "i";
					}

					pwDotFile.println(i + "->" + e.getNode().getIndex() + "[label=\""
							+ branchType + "_" + e.getOrdinal() + "\"]");
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

	}
	
	public static void dumpHashCollision(ExecutionGraph graph) {
		
	}
}
