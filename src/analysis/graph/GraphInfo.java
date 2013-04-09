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
	private 
	
	public void dumpGraph(String fileName) {
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

			for (int i = 0; i < nodes.size(); i++) {
				pwNodeFile.println(Long.toHexString(nodes.get(i).hash));
			}

			pwDotFile.println("digraph runGraph {");
			long firstMainBlock = outputFirstMain();
			pwDotFile.println("# First main block: "
					+ Long.toHexString(firstMainBlock));
			for (int i = 0; i < nodes.size(); i++) {
				// pw.println("node_" + Long.toHexString(nodes.get(i).hash));
				pwDotFile.println(i + "[label=\""
						+ Long.toHexString(nodes.get(i).hash) + "\"]");

				ArrayList<Edge> edges = nodes.get(i).edges;
				for (Edge e : edges) {
					String branchType;
					if (e.isDirect) {
						branchType = "d";
					} else {
						branchType = "i";
					}

					pwDotFile.println(i + "->" + e.node.index + "[label=\""
							+ branchType + "_" + e.ordinal + "\"]");
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
	
	
	private static void outputMergedGraphInfo(ExecutionGraph graph1,
			ExecutionGraph graph2, MatchedNodes matchedNodes) {
		System.out.println(graph1.nodes.size());
		System.out.println(graph2.nodes.size());

		HashSet<Long> interPairHashes = AnalysisUtil.intersection(
				graph1.pairHashes, graph2.pairHashes), interBlockHashes = AnalysisUtil
				.intersection(graph1.blockHashes, graph2.blockHashes);
		HashSet<Long> totalPairHashes = new HashSet(graph1.pairHashes);
		totalPairHashes.addAll(graph2.pairHashes);
		HashSet<Long> totalBlockHashes = new HashSet(graph1.blockHashes);
		totalBlockHashes.addAll(graph2.blockHashes);
		// System.out.println("Intersection ratio of pair hashes: "
		// + (float) interPairHashes.size() / totalPairHashes.size());
		System.out.println("Intersection ratio of block hashes: "
				+ (float) interBlockHashes.size() / totalBlockHashes.size());
		int totalNodeSize = graph1.nodes.size() + graph2.nodes.size()
				- matchedNodes.size();
		System.out.println("Merged nodes: " + matchedNodes.size());
		System.out.println("Merged nodes / G1 nodes: "
				+ (float) matchedNodes.size() / graph1.nodes.size());
		System.out.println("Merged nodes / G2 nodes: "
				+ (float) matchedNodes.size() / graph2.nodes.size());
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
	
	public long outputFirstMain() {
		Node n = null;
		long firstMainHash = -1;
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).hash == specialHash) {
				n = nodes.get(i);
				if (n.edges.size() > 1) {
					System.out.println("More than one target!");
					return n.edges.size();
				} else {
					firstMainHash = n.edges.get(0).node.hash;
				}
				break;
			}
		}
		return firstMainHash;
	}
	
	public void dumpHashCollision() {
		System.out.println(progName + "." + pid + " -> hash collision:");
		for (long hash : hash2Nodes.keySet()) {
			ArrayList<Node> nodes = hash2Nodes.get(hash);
			if (nodes.size() > 1) {

				if (nodes.get(0).hash == 0xff) {
					// System.out.println("Stop!");
					// for (int i = 0; i < nodes.size(); i++) {
					//
					// }
				}
				System.out.println("Hash collision for "
						+ Long.toHexString(nodes.get(0).hash) + ": "
						+ nodes.size());
			}
		}
	}
}
