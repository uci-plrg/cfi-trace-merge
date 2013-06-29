package analysis.graph.representation;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import utils.AnalysisUtil;
import analysis.exception.graph.InvalidTagException;
import analysis.exception.graph.MultipleEdgeException;
import analysis.exception.graph.OverlapModuleException;
import analysis.exception.graph.TagNotFoundException;
import analysis.graph.debug.DebugUtils;

public class ExecutionGraph {
	private HashSet<Long> pairHashes;
	private HashSet<Long> blockHashes;
	private ArrayList<Long> pairHashInstances;
	private ArrayList<Long> blockHashInstances;

	// This field is used to normalize the tag in a single graph
	private ArrayList<ModuleDescriptor> modules;

	// Maps from post-processed relative tag to the node,
	// only for the sake of debugging and analysis
	public HashMap<NormalizedTag, Node> normalizedTag2Node;

	private String pairHashFile;
	private String blockHashFile;
	private String runDir;
	private String progName;
	private int pid;

	// False means that the file doesn't exist or is in wrong format
	private boolean isValidGraph = true;

	// nodes in an array in the read order from file
	private ArrayList<Node> nodes;

	// Map from hash to ArrayList<Node>,
	// which also helps to find out the hash collisions
	private NodeHashMap hash2Nodes;

	public String toString() {
		return progName + pid;
	}

	public ArrayList<ModuleDescriptor> getModules() {
		return modules;
	}

	public ArrayList<Node> getNodes() {
		return nodes;
	}

	public NodeList getNodesByHash(long l) {
		return hash2Nodes.get(l);
	}

	// FIXME: Deep copy of a graph
	public ExecutionGraph(ExecutionGraph anotherGraph) {
		pairHashes = anotherGraph.pairHashes;
		blockHashes = anotherGraph.blockHashes;
		pairHashInstances = anotherGraph.pairHashInstances;
		blockHashInstances = anotherGraph.blockHashInstances;

		pairHashFile = anotherGraph.pairHashFile;
		blockHashFile = anotherGraph.blockHashFile;
		runDir = anotherGraph.runDir;
		progName = anotherGraph.progName;
		pid = anotherGraph.pid;

		isValidGraph = anotherGraph.isValidGraph;

		// Copy the nodes, but the edges are not yet copied...
		nodes = new ArrayList<Node>(anotherGraph.nodes.size());
		for (int i = 0; i < anotherGraph.nodes.size(); i++) {
			nodes.add(new Node(this, anotherGraph.nodes.get(i)));
		}
		// Copy the edges of each nodes
		for (int i = 0; i < anotherGraph.nodes.size(); i++) {
			Node anotherNode = anotherGraph.nodes.get(i);
			// Traverse all the edges by outgoing edges
			for (int j = 0; j < anotherNode.getOutgoingEdges().size(); j++) {
				Edge e = anotherNode.getOutgoingEdges().get(j);
				Node n1 = nodes.get(e.getFromNode().getIndex()), n2 = nodes
						.get(e.getToNode().getIndex());
				Edge newEdge = new Edge(n1, n2, e.getEdgeType(), e.getOrdinal());
				n1.addOutgoingEdge(newEdge);
				n2.addIncomingEdge(newEdge);
			}
		}

		// Copy hash2Nodes field
		hash2Nodes = new NodeHashMap();
		for (long l : anotherGraph.hash2Nodes.keySet()) {
			NodeList anotherNodes = anotherGraph.hash2Nodes.get(l);
			hash2Nodes.add(l, anotherNodes.copy(this));
		}

		// Make sure the reachable field is set after being copied, might be
		// redundant
		setReachableNodes();
	}

	/**
	 * Traverse the graph to decide if each node is reachable from the entry
	 * node. This method must be called after the graph has been constructed.
	 */
	private void setReachableNodes() {
		for (int i = 0; i < nodes.size(); i++) {
			nodes.get(i).resetVisited();
			nodes.get(i).setReachable(false);
		}
		Queue<Node> bfsQueue = new LinkedList<Node>();
		bfsQueue.add(nodes.get(0));
		nodes.get(0).setVisited();
		while (bfsQueue.size() > 0) {
			Node n = bfsQueue.remove();
			n.setReachable(true);
			for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
				Node neighbor = n.getOutgoingEdges().get(i).getToNode();
				if (!neighbor.isVisited()) {
					bfsQueue.add(neighbor);
					neighbor.setVisited();
				}
			}
		}
	}

	// Add a node with hashcode hash and return the newly
	// created node
	public Node addNode(long hash, MetaNodeType metaNodeType) {
		Node n = new Node(this, hash, nodes.size(), metaNodeType);
		nodes.add(n);
		hash2Nodes.add(n);
		return n;
	}

	public void addEdge(Node from, Edge e) {
		from.addOutgoingEdge(e);
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
		hash2Nodes = new NodeHashMap();
		blockHashes = new HashSet<Long>();
		pairHashes = new HashSet<Long>();
	}

	public ExecutionGraph(ArrayList<String> tagFiles,
			ArrayList<String> lookupFiles) {
		nodes = new ArrayList<Node>();
		hash2Nodes = new NodeHashMap();
		this.progName = AnalysisUtil.getProgName(tagFiles.get(0));
		this.runDir = AnalysisUtil.getRunStr(tagFiles.get(0));
		this.pid = AnalysisUtil.getPidFromFileName(tagFiles.get(0));

		// The edges of the graph comes with an ordinal
		HashMap<Long, Node> hashLookupTable;
		try {
			hashLookupTable = readGraphLookup(lookupFiles);
			readGraph(tagFiles, hashLookupTable);
		} catch (InvalidTagException e) {
			System.out.println("This is not a valid graph!!!");
			isValidGraph = false;
			e.printStackTrace();
		} catch (TagNotFoundException e) {
			System.out.println("This is not a valid graph!!!");
			isValidGraph = false;
			e.printStackTrace();
		} catch (MultipleEdgeException e) {
			System.out.println("This is not a valid graph!!!");
			isValidGraph = false;
			e.printStackTrace();
		}

		// Some other initialization and sanity checks
		setReachableNodes();
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

	public String getRunDir() {
		return runDir;
	}

	public void setProgName(String progName) {
		this.progName = progName;
	}

	public boolean isValidGraph() {
		return isValidGraph;
	}

	private HashMap<Long, Node> readGraphLookup(ArrayList<String> lookupFiles)
			throws InvalidTagException {
		HashMap<Long, Node> hashLookupTable = new HashMap<Long, Node>();

		FileInputStream fileIn = null;
		FileChannel channel = null;
		ByteBuffer buffer = ByteBuffer.allocate(0x8);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		for (int i = 0; i < lookupFiles.size(); i++) {
			String lookupFile = lookupFiles.get(i);
			try {
				fileIn = new FileInputStream(lookupFile);
				channel = fileIn.getChannel();
				long tag = 0, tagOriginal = 0, hash = 0;

				while (true) {
					if (channel.read(buffer) < 0)
						break;
					buffer.flip();
					tagOriginal = buffer.getLong();
					buffer.compact();

					channel.read(buffer);
					buffer.flip();
					hash = buffer.getLong();
					buffer.compact();

					// the tag and hash here is already a big-endian value
					// tagOriginal = AnalysisUtil.reverseForLittleEndian(dataIn
					// .readLong());
					// hash = AnalysisUtil.reverseForLittleEndian(dataIn
					// .readLong());
					// System.out.println(Long.toHexString(tagOriginal));
					// System.out.println(Long.toHexString(hash));

					tag = getTagEffectiveValue(tagOriginal);
					int metaNodeVal = getNodeMetaVal(tagOriginal);
					MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];

					// Tags don't duplicate in lookup file
					if (hashLookupTable.containsKey(tag)) {
						if (hashLookupTable.get(tag).getHash() != hash) {
							isValidGraph = false;
							String msg = "Duplicate tags: "
									+ Long.toHexString(tag)
									+ " -> "
									+ Long.toHexString(hashLookupTable.get(tag)
											.getHash()) + ":"
									+ Long.toHexString(hash) + "  "
									+ lookupFile;
							if (DebugUtils.ThrowInvalidTag) {
								throw new InvalidTagException(msg);
							}
						}
					}
					Node node = new Node(this, tag, hash, nodes.size(),
							metaNodeType);
					hashLookupTable.put(tag, node);
					nodes.add(node);

					// Add it the the hash2Nodes mapping
					hash2Nodes.add(node);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {

			} catch (IOException e) {
				e.printStackTrace();
			}
			if (fileIn != null) {
				try {
					fileIn.close();
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

	public boolean isTailNode(Node n) {
		return isTailNode(n, 10);
	}

	public boolean isTailNode(Node n, int level) {
		if (level == 0) {
			return false;
		}
		ArrayList<Edge> outgoingEdges = n.getOutgoingEdges();
		if (outgoingEdges.size() == 0) {
			return true;
		}
		for (int i = 0; i < outgoingEdges.size(); i++) {
			if (!isTailNode(outgoingEdges.get(i).getToNode(), level - 1)) {
				return false;
			}
		}
		return true;
	}

	public void readGraph(ArrayList<String> tagFiles,
			HashMap<Long, Node> hashLookupTable) throws InvalidTagException,
			TagNotFoundException, MultipleEdgeException {

		for (int i = 0; i < tagFiles.size(); i++) {
			String tagFile = tagFiles.get(i);
			File file = new File(tagFile);
			FileInputStream fileIn = null;
			ByteBuffer buffer = ByteBuffer.allocate(0x8);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			// Track how many tags does not exist in lookup file
			HashSet<Long> hashesNotInLookup = new HashSet<Long>();
			try {
				fileIn = new FileInputStream(file);
				FileChannel channel = fileIn.getChannel();

				while (true) {
					if (channel.read(buffer) < 0)
						break;
					buffer.flip();
					long tag1 = buffer.getLong();
					buffer.compact();
					int flags = getEdgeFlag(tag1);
					tag1 = getTagEffectiveValue(tag1);

					channel.read(buffer);
					buffer.flip();
					long tag2Original = buffer.getLong();
					buffer.compact();
					long tag2 = getTagEffectiveValue(tag2Original);
					if (tag2 != tag2Original) {
						if (DebugUtils.ThrowInvalidTag) {
							throw new InvalidTagException("Tag 0x"
									+ Long.toHexString(tag2Original)
									+ " has more than 6 bytes");
						}
					}

					Node node1 = hashLookupTable.get(tag1), node2 = hashLookupTable
							.get(tag2);

					// Double check if tag1 and tag2 exist in the lookup file
					if (node1 == null) {
						hashesNotInLookup.add(tag1);
						if (DebugUtils.ThrowTagNotFound) {
							throw new TagNotFoundException("0x"
									+ Long.toHexString(tag1)
									+ " is missed in graph lookup file!");
						}
					}
					if (node2 == null) {
						hashesNotInLookup.add(tag2);
						if (DebugUtils.ThrowTagNotFound) {
							throw new TagNotFoundException("0x"
									+ Long.toHexString(tag2)
									+ " is missed in graph lookup file!");
						}
					}
					if (node1 == null || node2 == null) {
						if (!DebugUtils.ThrowTagNotFound) {
							continue;
						}
					}

					Edge existing = node1.getOutgoingEdge(node2);
					if (existing == null) {
						Edge e = new Edge(node1, node2, flags);
						node1.addOutgoingEdge(e);
						node2.addIncomingEdge(e);
					} else {
						if (!existing.hasFlags(flags)) {
							String msg = "Multiple edges:\n" + "Edge1: "
									+ node1.getHash() + "->" + node2.getHash()
									+ ": " + existing.getToNode() + "Edge2: "
									+ node1.getHash() + "->" + node2.getHash()
									+ ": " + flags;
							if (DebugUtils.ThrowMultipleEdge) {
								throw new MultipleEdgeException(msg);
							}
						}
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {
				// System.out.println("Finiish reading the file: " + fileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (hashesNotInLookup.size() != 0) {
				// For now, the missing lookup entry is small, just skip it
				// isValidGraph = false;
				// System.out.println(hashesNotInLookup.size()
				// + " tag doesn't exist in lookup file -> " + tagFile);
			}

			if (fileIn != null) {
				try {
					fileIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// Since the vertices will never change once the graph is created
		nodes.trimToSize();
	}

	public static ArrayList<ExecutionGraph> buildGraphsFromRunDir(String dir) {
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();

		File dirFile = new File(dir);
		String[] fileNames = dirFile.list();
		HashMap<Integer, ArrayList<String>> pid2LookupFiles = new HashMap<Integer, ArrayList<String>>(), pid2TagFiles = new HashMap<Integer, ArrayList<String>>();
		HashMap<Integer, String> pid2PairHashFile = new HashMap<Integer, String>(), pid2BlockHashFile = new HashMap<Integer, String>();
		HashMap<Integer, String> pid2ModuleFile = new HashMap<Integer, String>();

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
			} else if (fileNames[i].indexOf("module") != -1) {
				pid2ModuleFile.put(pid, dir + "/" + fileNames[i]);
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

			// Read the modules from file
			try {
				graph.modules = AnalysisUtil
						.getModules(pid2ModuleFile.get(pid));
			} catch (OverlapModuleException e) {
				e.printStackTrace();
			}

			// Initialize the relativeTag2Node hashtable
			// This is only used for debugging so far
			graph.normalizedTag2Node = new HashMap<NormalizedTag, Node>();
			for (int i = 0; i < graph.nodes.size(); i++) {
				Node n = graph.nodes.get(i);
				long relativeTag = AnalysisUtil.getRelativeTag(graph,
						n.getTag());
				String moduleName = AnalysisUtil.getModuleName(graph,
						n.getTag());
				graph.normalizedTag2Node.put(new NormalizedTag(moduleName,
						relativeTag), n);
			}

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

	public ArrayList<Node> getAccessibleNodes() {
		ArrayList<Node> accessibleNodes = new ArrayList<Node>();
		for (int i = 0; i < nodes.size(); i++) {
			nodes.get(i).resetVisited();
		}
		Queue<Node> bfsQueue = new LinkedList<Node>();
		bfsQueue.add(nodes.get(0));
		nodes.get(0).setVisited();
		while (bfsQueue.size() > 0) {
			Node n = bfsQueue.remove();
			accessibleNodes.add(n);
			for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
				Node neighbor = n.getOutgoingEdges().get(i).getToNode();
				if (!neighbor.isVisited()) {
					bfsQueue.add(neighbor);
					neighbor.setVisited();
				}
			}
		}
		return accessibleNodes;
	}

	public ArrayList<Node> getDanglingNodes() {
		ArrayList<Node> danglingNodes = new ArrayList<Node>();
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (n.getIncomingEdges().size() == 0
					&& n.getOutgoingEdges().size() == 0)
				danglingNodes.add(n);
		}
		return danglingNodes;
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
				if (n.getOutgoingEdges().size() != 0) {
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

	/**
	 * This function is used to prune the super graph ---- big merged graph.
	 * Since our strategy now is to tolerate aliasing of nodes, we then need to
	 * compress the graph and avoid the graph to increase infinitely.
	 */
	public void compress() {

	}

}