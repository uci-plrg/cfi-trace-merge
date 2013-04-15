package analysis.graph.representation;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import utils.AnalysisUtil;
import analysis.graph.representation.*;

public class ExecutionGraph {
	private HashSet<Long> pairHashes;
	private HashSet<Long> blockHashes;
	private ArrayList<Long> pairHashInstances;
	private ArrayList<Long> blockHashInstances;

	private String pairHashFile;
	private String blockHashFile;
	private String runDirName;
	private String progName;
	private int pid;

	// False means that the file doesn't exist or is in wrong format
	private boolean isValidGraph = true;

	// nodes in an array in the read order from file
	private ArrayList<Node> nodes;

	// Map from hash to ArrayList<Node>,
	// which also helps to find out the hash collisions
	private HashMap<Long, ArrayList<Node>> hash2Nodes;

	public ArrayList<Node> getNodes() {
		return nodes;
	}

	public HashMap<Long, ArrayList<Node>> getHash2Nodes() {
		return hash2Nodes;
	}

	// FIXME: Deep copy of a graph
	public ExecutionGraph(ExecutionGraph anotherGraph) {

	}

	// Add a node with hashcode hash and return the newly
	// created node
	public Node addNode(long hash, MetaNodeType metaNodeType) {
		Node n = new Node(hash, nodes.size(), metaNodeType);
		nodes.add(n);
		if (!hash2Nodes.containsKey(hash)) {
			hash2Nodes.put(hash, new ArrayList<Node>());
		}
		hash2Nodes.get(hash).add(n);
		return n;
	}

	public void addEdge(Node from, Edge e) {
		from.addEdge(e);
	}

	public void addBlockHash(ExecutionGraph anotherGraph) {
		if (anotherGraph == null || anotherGraph.blockHashes == null)
			return;
		blockHashes.addAll(anotherGraph.blockHashes);
	}

	public void addPairHash(ExecutionGraph anotherGraph) {
		pairHashes.addAll(anotherGraph.pairHashes);
	}

	public HashSet<Long> getBlockHashes() {
		return blockHashes;
	}

	public HashSet<Long> getPairHashes() {
		return pairHashes;
	}

	public ExecutionGraph() {
		nodes = new ArrayList<Node>();
		hash2Nodes = new HashMap<Long, ArrayList<Node>>();
		blockHashes = new HashSet<Long>();
		pairHashes = new HashSet<Long>();
	}

	public ExecutionGraph(ArrayList<String> tagFiles,
			ArrayList<String> lookupFiles) {
		nodes = new ArrayList<Node>();
		hash2Nodes = new HashMap<Long, ArrayList<Node>>();
		this.progName = AnalysisUtil.getProgName(tagFiles.get(0));
		this.pid = AnalysisUtil.getPidFromFileName(tagFiles.get(0));

		// The edges of the graph comes with an ordinal
		HashMap<Long, Node> hashLookupTable = readGraphLookup(lookupFiles);
		readGraph(tagFiles, hashLookupTable);

		validate();
		if (!isValidGraph) {
			System.out.println("Pid " + pid + " is not a valid graph!");
		}
	}

	public int getPid() {
		return pid;
	}

	public String getProgName() {
		return progName;
	}

	public void setProgName(String progName) {
		this.progName = progName;
	}

	public boolean isValidGraph() {
		return isValidGraph;
	}

	private HashMap<Long, Node> readGraphLookup(ArrayList<String> lookupFiles) {
		HashMap<Long, Node> hashLookupTable = new HashMap<Long, Node>();

		FileInputStream fileIn = null;
		DataInputStream dataIn = null;

		for (int i = 0; i < lookupFiles.size(); i++) {
			String lookupFile = lookupFiles.get(i);
			try {
				fileIn = new FileInputStream(lookupFile);
				dataIn = new DataInputStream(fileIn);
				long tag = 0, tagOriginal = 0, hash = 0;
				while (true) {
					// the tag and hash here is already a big-endian value
					tagOriginal = AnalysisUtil.reverseForLittleEndian(dataIn
							.readLong());
					hash = AnalysisUtil.reverseForLittleEndian(dataIn
							.readLong());
					// System.out.println(Long.toHexString(tagOriginal));
					// System.out.println(Long.toHexString(hash));

					tag = getTagEffectiveValue(tagOriginal);
					int metaNodeVal = getNodeMetaVal(tagOriginal);
					MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];

					// Tags don't duplicate in lookup file
					if (hashLookupTable.containsKey(tag)) {
						if (hashLookupTable.get(tag).getHash() != hash) {
							isValidGraph = false;
							System.out.println(Long.toHexString(tag)
									+ " -> "
									+ Long.toHexString(hashLookupTable.get(tag)
											.getHash()) + ":"
									+ Long.toHexString(hash) + "  "
									+ lookupFile);
						}
					}
					Node node = new Node(tag, hash, nodes.size(), metaNodeType);
					hashLookupTable.put(tag, node);
					nodes.add(node);

					// Add it the the hash2Nodes mapping
					if (hash2Nodes.get(hash) == null) {
						hash2Nodes.put(hash, new ArrayList<Node>());
					}
					if (!hash2Nodes.get(hash).contains(node)) {
						hash2Nodes.get(hash).add(node);
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {

			} catch (IOException e) {
				e.printStackTrace();
			}
			if (dataIn != null) {
				try {
					dataIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return hashLookupTable;
	}

	// Return the highest two bytes
	public static int getEdgeFlag(long tag) {
		return new Long(tag >>> 48).intValue();
	}

	public static int getNodeMetaVal(long tag) {
		return new Long(tag >>> 56).intValue();
	}

	// get the lower 6 byte of the tag, which is a long integer
	public static long getTagEffectiveValue(long tag) {
		Long res = tag << 16 >>> 16;
		return res;
	}

	public void readGraph(ArrayList<String> tagFiles,
			HashMap<Long, Node> hashLookupTable) throws NullPointerException {

		HashMap<Node, HashMap<Node, Integer>> adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		for (int i = 0; i < tagFiles.size(); i++) {
			String tagFile = tagFiles.get(i);
			File file = new File(tagFile);
			FileInputStream fileIn = null;
			DataInputStream dataIn = null;
			// to track how many tags does not exist in lookup file
			HashSet<Long> hashesNotInLookup = new HashSet<Long>();
			try {
				fileIn = new FileInputStream(file);
				dataIn = new DataInputStream(fileIn);
				while (true) {
					long tag1 = AnalysisUtil.reverseForLittleEndian(dataIn
							.readLong());
					int flag = getEdgeFlag(tag1);
					tag1 = getTagEffectiveValue(tag1);
					long tag2Original = AnalysisUtil
							.reverseForLittleEndian(dataIn.readLong());
					long tag2 = getTagEffectiveValue(tag2Original);
					if (tag2 != tag2Original) {
						System.out.println("Something wrong about the tag");
					}

					Node node1 = hashLookupTable.get(tag1), node2 = hashLookupTable
							.get(tag2);

					// double check if tag1 and tag2 exist in the lookup file
					if (node1 == null) {
						hashesNotInLookup.add(tag1);
					}
					if (node2 == null) {
						hashesNotInLookup.add(tag2);
					}
					if (node1 == null || node2 == null) {
						// System.out.println("Missing lookup entry in hash lookup file!");
						// System.out.println(Long.toHexString(tag1));
						// System.out.println(Long.toHexString(tag2));
						continue;
					}

					// also put the nodes into the adjacentList if they are not
					// stored yet
					// add node to an array, which is in their seen order in the
					// file
					if (!adjacentList.containsKey(node1)) {
						adjacentList.put(node1, new HashMap<Node, Integer>());
					}

					HashMap<Node, Integer> edges = adjacentList.get(node1);
					// Also update the ArrayList<Edge> of node
					if (!edges.containsKey(node2)) {
						edges.put(node2, flag);
						node1.addEdge(new Edge(node2, flag));
						node2.addIncomingEdge(new Edge(node1, flag));
					} else {
						System.out.println("Multiple edges!!");
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {
				// System.out.println("Finish reading the file: " + fileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (hashesNotInLookup.size() != 0) {
				// For now, the missing lookup entry is small, just skip it
//				isValidGraph = false;
//				System.out.println(hashesNotInLookup.size()
//						+ " tag doesn't exist in lookup file -> " + tagFile);
			}

			if (dataIn != null) {
				try {
					dataIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// since V will never change once the graph is created
		nodes.trimToSize();

	}

	public static ArrayList<ExecutionGraph> buildGraphsFromRunDir(String dir) {
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();

		File dirFile = new File(dir);
		String[] fileNames = dirFile.list();
		HashMap<Integer, ArrayList<String>> pid2LookupFiles = new HashMap<Integer, ArrayList<String>>(), pid2TagFiles = new HashMap<Integer, ArrayList<String>>();
		HashMap<Integer, String> pid2PairHashFile = new HashMap<Integer, String>(), pid2BlockHashFile = new HashMap<Integer, String>();

		for (int i = 0; i < fileNames.length; i++) {
			int pid = AnalysisUtil.getPidFromFileName(fileNames[i]);
			if (pid == 0)
				continue;
			if (pid2LookupFiles.get(pid) == null) {
				pid2LookupFiles.put(pid, new ArrayList<String>());
				pid2TagFiles.put(pid, new ArrayList<String>());
			}
			if (fileNames[i].indexOf("bb-graph-hash.") != -1) {
				pid2LookupFiles.get(pid).add(dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("bb-graph.") != -1) {
				pid2TagFiles.get(pid).add(dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("pair-hash") != -1) {
				pid2PairHashFile.put(pid, dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("block-hash") != -1) {
				pid2BlockHashFile.put(pid, dir + "/" + fileNames[i]);
			}
		}

		// Build the graphs
		for (int pid : pid2LookupFiles.keySet()) {

			ArrayList<String> lookupFiles = pid2LookupFiles.get(pid), tagFiles = pid2TagFiles
					.get(pid);
			if (lookupFiles.size() == 0)
				continue;
			String possibleProgName = AnalysisUtil.getProgName(lookupFiles
					.get(0));
			ExecutionGraph graph = new ExecutionGraph(tagFiles, lookupFiles);

			// Initialize hash files and hash sets
			graph.pairHashFile = pid2PairHashFile.get(pid);
			graph.blockHashFile = pid2BlockHashFile.get(pid);
			graph.pairHashes = AnalysisUtil.getSetFromPath(graph.pairHashFile);
			graph.blockHashes = AnalysisUtil
					.getSetFromPath(graph.blockHashFile);
			graph.pairHashInstances = AnalysisUtil
					.getAllHashInstanceFromPath(graph.pairHashFile);
			graph.blockHashInstances = AnalysisUtil
					.getAllHashInstanceFromPath(graph.blockHashFile);

			graph.progName = possibleProgName;
			graph.pid = pid;

			if (!graph.isValidGraph) {
				System.out.println("Pid " + pid + " is not a valid graph!");
			}

			graphs.add(graph);
		}
		return graphs;
	}

	public static ArrayList<ExecutionGraph> getGraphs(String dir) {
		File file = new File(dir);
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();

		for (File runDir : file.listFiles()) {
			graphs.addAll(buildGraphsFromRunDir(runDir.getAbsolutePath()));
		}
		return graphs;
	}

	/**
	 * To validate the correctness of the graph. Basically it checks if entry
	 * points have no incoming edges, exit points have no outgoing edges. It
	 * might include more validation stuff later...
	 * 
	 * @return true means this is a valid graph, otherwise it's invalid
	 */
	public boolean validate() {
		outerLoop: for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			switch (n.getMetaNodeType()) {
			case ENTRY:
				if (n.getIncomingEdges().size() != 0) {
					System.out.println("Entry point has incoming edges!");
					isValidGraph = false;
					break outerLoop;
				}
				break;
			case EXIT:
				if (n.getEdges().size() != 0) {
					System.out.println("Exit point has outgoing edges!");
					isValidGraph = false;
					break outerLoop;
				}
				break;
			default:
				break;
			}
		}
		return isValidGraph;
	}

}