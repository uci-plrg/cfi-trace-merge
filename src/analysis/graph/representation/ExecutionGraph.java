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

/**
 * <p>
 * This class abstracts the binary-level labeled control flow graph of any
 * execution of a binary executable.
 * </p>
 * 
 * <p>
 * There are a few assumptions: 1. Within one execution, the tag, which is
 * address of the block of code in the code cache of DynamoRIO, can uniquely
 * represents an actual block of code in run-time memory. This might not be true
 * if the same address has different pieces of code at different time. 2. In
 * windows, we already have a list of known core utility DLL's, which means we
 * will match modules according to the module names plus its version number.
 * This might not be a universally true assumption, but it's still reasonable at
 * this point. We will treat unknown modules as inline code, which is part of
 * the main graph.
 * </p>
 * 
 * <p>
 * This class will have a list of its subclass, ModuleGraph, which is the graph
 * representation of each run-time module.
 * </p>
 * 
 * <p>
 * This class should have the signature2Node filed which maps the signature hash
 * to the bogus signature node. The basic matching strategy separates the main
 * module and all other kernel modules. All these separate graphs have a list of
 * callbacks or export functions from other modules, which have a corresponding
 * signature hash. For those nodes, we try to match them according to their
 * signature hash.
 * </p>
 * 
 * @author peizhaoo
 * 
 */

public class ExecutionGraph {
	protected HashSet<Long> blockHashes;

	private HashSet<Long> totalBlockHashes;

	// Represents the list of core modules
	private HashMap<String, ModuleGraph> moduleGraphs;

	// Used to normalize the tag in a single graph
	protected ArrayList<ModuleDescriptor> modules;

	// Maps from post-processed relative tag to the node,
	// only for the sake of debugging and analysis
	public HashMap<NormalizedTag, Node> normalizedTag2Node;

	protected String runDir;
	protected String progName;
	protected int pid;

	// Maps from signature hash to bogus signature node
	protected HashMap<Long, Node> signature2Node;

	// False means that the file doesn't exist or is in wrong format
	protected boolean isValidGraph = true;

	// nodes in an array in the read order from file
	protected ArrayList<Node> nodes;

	// Map from hash to ArrayList<Node>,
	// which also helps to find out the hash collisions
	protected NodeHashMap hash2Nodes;

	public void setRunDir(String runDir) {
		this.runDir = runDir;
	}

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

	public HashMap<String, ModuleGraph> getModuleGraphs() {
		return moduleGraphs;
	}

	public HashMap<Long, Node> getSigature2Node() {
		return signature2Node;
	}

	// FIXME: Deep copy of a graph, but probably we don't need to call this
	// function
	public ExecutionGraph(ExecutionGraph anotherGraph) {
		blockHashes = anotherGraph.blockHashes;
		totalBlockHashes = anotherGraph.totalBlockHashes;

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
	}

	// Add a node with hashcode hash and return the newly
	// created node
	public Node addNode(long hash, MetaNodeType metaNodeType,
			NormalizedTag normalizedTag) {
		Node n = new Node(this, hash, nodes.size(), metaNodeType, normalizedTag);
		nodes.add(n);

		if (metaNodeType != MetaNodeType.SIGNATURE_HASH) {
			// hash2Nodes only maps hash of code block to real nodes
			hash2Nodes.add(n);
		} else if (!signature2Node.containsKey(hash)) {
			signature2Node.put(hash, n);
		}
		return n;
	}

	// Add the signature node to the graph
	public void addSignatureNode(long sigHash) {
		if (!signature2Node.containsKey(sigHash)) {
			Node sigNode = new Node(this, sigHash, nodes.size(),
					MetaNodeType.SIGNATURE_HASH, NormalizedTag.blankTag);
			signature2Node.put(sigNode.getHash(), sigNode);
			sigNode.setIndex(nodes.size());
			nodes.add(sigNode);
		}
	}

	public void addEdge(Node from, Edge e) {
		from.addOutgoingEdge(e);
	}

	public void addBlockHash(ExecutionGraph anotherGraph) {
		if (anotherGraph == null || anotherGraph.blockHashes == null)
			return;
		blockHashes.addAll(anotherGraph.blockHashes);
	}

	public HashSet<Long> getBlockHashes() {
		return blockHashes;
	}

	public HashSet<Long> getTotalBlockHashes() {
		return totalBlockHashes;
	}

	/**
	 * This is a blank constructor which should be used when combining two
	 * matched graph. After calling it, you will get only an empty graph, thus
	 * be really careful to add nodes and edges to it to avoid any kind of
	 * inconsistency in the new-built graph.
	 */
	public ExecutionGraph() {
		nodes = new ArrayList<Node>();
		hash2Nodes = new NodeHashMap();
		signature2Node = new HashMap<Long, Node>();
		blockHashes = new HashSet<Long>();
	}

	/**
	 * The construction constructs the ExecutionGraph from a variety of files
	 * located in the run directory
	 * 
	 * @param intraModuleEdgeFiles
	 *            <p>
	 *            The files containing all the intra-module edges in the format
	 *            of (tag1-->tag) entry, which takes 16 bytes
	 *            </p>
	 * @param lookupFiles
	 *            <p>
	 *            The files containing the mapping entry from a tag value to the
	 *            hash code of the basic block
	 * @param crossModuleEdgeFile
	 *            <p>
	 *            The files containing all the cross-module edges in the format
	 *            of (tag-->tag, Signiture Hash) entry, which takes 24 bytes
	 *            </p>
	 */
	public ExecutionGraph(ArrayList<String> intraModuleEdgeFiles,
			String crossModuleEdgeFile, ArrayList<String> lookupFiles,
			String moduleFile) {
		signature2Node = new HashMap<Long, Node>();
		moduleGraphs = new HashMap<String, ModuleGraph>();
		nodes = new ArrayList<Node>();
		hash2Nodes = new NodeHashMap();
		blockHashes = new HashSet<Long>();
		totalBlockHashes = new HashSet<Long>();

		this.progName = AnalysisUtil.getProgName(intraModuleEdgeFiles.get(0));
		this.runDir = AnalysisUtil.getRunStr(intraModuleEdgeFiles.get(0));
		this.pid = AnalysisUtil.getPidFromFileName(intraModuleEdgeFiles.get(0));

		// Read the modules from file
		try {
			modules = AnalysisUtil.getModules(moduleFile);
		} catch (OverlapModuleException e) {
			e.printStackTrace();
		}

		// The edges of the graph comes with an ordinal
		HashMap<VersionedTag, Node> hashLookupTable;
		try {
			// Construct the tag--hash lookup table
			hashLookupTable = readGraphLookup(lookupFiles);
			// Then read both intra- and cross- module edges from files
			readIntraModuleEdges(intraModuleEdgeFiles, hashLookupTable);
			readCrossModuleEdges(crossModuleEdgeFile, hashLookupTable);

			// Since the vertices will never change once the graph is created
			nodes.trimToSize();
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
		validate();
		// Produce some analysis result for the graph
		analyzeGraph();
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

	/**
	 * Given a full module name, which is the module name with the version
	 * number, e.g. comctl32.dll-1db1446a0006000a, this function will check if
	 * this module is a core module
	 * 
	 * @param fullModuleName
	 * @return
	 */
	private boolean isCoreModule(String fullModuleName) {
		if (fullModuleName.equals("Unknown")) {
			return false;
		}
		int idx = fullModuleName.lastIndexOf('-');
		String modName = fullModuleName.substring(0, idx);
		return ModuleDescriptor.coreModuleNames.contains(modName);
	}

	/**
	 * <p>
	 * The format of the lookup file is as the following:
	 * </p>
	 * <p>
	 * Each entry consists of 8-byte tag + 8-byte hash.
	 * </p>
	 * <p>
	 * 8-byte tag: 1-byte version number | 1-byte node type | 6-byte tag
	 * </p>
	 * 
	 * 
	 * @param lookupFiles
	 * @return
	 * @throws InvalidTagException
	 */
	private HashMap<VersionedTag, Node> readGraphLookup(
			ArrayList<String> lookupFiles) throws InvalidTagException {
		HashMap<VersionedTag, Node> hashLookupTable = new HashMap<VersionedTag, Node>();

		// Just a ad-hoc patch for the bug in the graph lookup file
		HashMap<Long, Integer> tag2CurrentVersion_ = new HashMap<Long, Integer>();

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
				String nodeModuleName;

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

					tag = getTagEffectiveValue(tagOriginal);
					int versionNumber = getNodeVersionNumber(tagOriginal), metaNodeVal = getNodeMetaVal(tagOriginal);
					VersionedTag versionedTag = new VersionedTag(tag,
							versionNumber);
					// Only for the ad-hoc fix of graph lookup file
					if (!tag2CurrentVersion_.containsKey(tag)) {
						tag2CurrentVersion_.put(tag, 0);
					} else if (tag2CurrentVersion_.get(tag) < versionNumber) {
						tag2CurrentVersion_.put(tag, versionNumber);
					}

					MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];

					// Tags don't duplicate in lookup file
					if (hashLookupTable.containsKey(versionedTag)) {
						Node existingNode = hashLookupTable.get(versionedTag);
						if (existingNode.getHash() != hash) {
							// Patch the buggy tag version manually
							if (versionedTag.versionNumber == 0
									&& tag2CurrentVersion_.get(tag) > 0) {
								versionNumber = tag2CurrentVersion_.get(tag);
								versionedTag = new VersionedTag(tag,
										versionNumber);
							} else {
								isValidGraph = false;
								String msg = "Duplicate tags: "
										+ versionedTag
										+ " -> "
										+ Long.toHexString(hashLookupTable.get(
												versionedTag).getHash()) + ":"
										+ Long.toHexString(hash) + "  "
										+ lookupFile;
								if (DebugUtils.ThrowInvalidTag) {
									throw new InvalidTagException(msg);
								}
							}
						}
					}
					nodeModuleName = AnalysisUtil.getModuleName(this, tag);

					// Be careful about the index here!!!
					// If the node is in a core module, the addModuleNode
					// function should fix the index.
					Node node = new Node(this, versionedTag, hash,
							nodes.size(), metaNodeType);

					if (isCoreModule(nodeModuleName)) {
						ModuleGraph moduleGraph;
						if (!moduleGraphs.containsKey(nodeModuleName)) {
							moduleGraph = new ModuleGraph(nodeModuleName, pid,
									modules);
							moduleGraph.setRunDir(runDir);
							moduleGraphs.put(nodeModuleName, moduleGraph);
						}
						moduleGraph = moduleGraphs.get(nodeModuleName);
						moduleGraph.addModuleNode(node);
						if (!node.getNormalizedTag().moduleName
								.equals("Unknown")) {
							moduleGraph.blockHashes.add(node.getHash());
						}
					} else {
						if (!node.getNormalizedTag().moduleName
								.equals("Unknown")) {
							blockHashes.add(node.getHash());
						}
						nodes.add(node);
						// Add it the the hash2Nodes mapping
						hash2Nodes.add(node);
					}
					hashLookupTable.put(versionedTag, node);
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
		// Store the total block hash code
		for (VersionedTag versionedTag : hashLookupTable.keySet()) {
			totalBlockHashes.add(hashLookupTable.get(versionedTag).getHash());
		}

		return hashLookupTable;
	}

	// Return ordinal of the edge by passing the from tag
	public static int getEdgeOrdinal(long tag) {
		return new Long(tag << 16 >>> 56).intValue();
	}

	// Return type of the edge by passing the from tag
	public static EdgeType getTagEdgeType(long tag) {
		int ordinal = new Long(tag << 8 >>> 56).intValue();
		return EdgeType.values()[ordinal];
	}

	// Return the effective address of the tag
	public static long getTagEffectiveValue(long tag) {
		return new Long(tag << 24 >>> 24).intValue();
	}

	public static int getNodeVersionNumber(long tag) {
		return new Long(tag >>> 56).intValue();
	}

	public static int getNodeMetaVal(long tag) {
		return new Long(tag << 8 >>> 56).intValue();
	}

	// get the lower 6 byte of the tag, which is a long integer
	public static VersionedTag getVersionedTag(long tag) {
		long tagLong = tag << 24 >>> 24;
		int versionNumber = (new Long(tag >>> 56)).intValue();
		return new VersionedTag(tagLong, versionNumber);
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

	/**
	 * Before calling this function, you should have all the normal nodes added
	 * to the corresponding graph and their indexes fixed. The only thing this
	 * function should do is to add signature nodes when necessary and build the
	 * necessary edges between them and real entry nodes.
	 * 
	 * @param crossModuleEdgeFile
	 * @param hashLookupTable
	 * @throws MultipleEdgeException
	 * @throws InvalidTagException
	 * @throws TagNotFoundException
	 */
	public void readCrossModuleEdges(String crossModuleEdgeFile,
			HashMap<VersionedTag, Node> hashLookupTable)
			throws MultipleEdgeException, InvalidTagException,
			TagNotFoundException {
		File file = new File(crossModuleEdgeFile);
		FileInputStream fileIn = null;
		ByteBuffer buffer = ByteBuffer.allocate(0x8);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		try {
			fileIn = new FileInputStream(file);
			FileChannel channel = fileIn.getChannel();

			while (true) {
				if (channel.read(buffer) < 0)
					break;
				buffer.flip();
				long tag1 = buffer.getLong();
				buffer.compact();

				channel.read(buffer);
				buffer.flip();
				long tag2 = buffer.getLong();
				buffer.compact();

				channel.read(buffer);
				buffer.flip();
				long signatureHash = buffer.getLong();
				buffer.compact();

				VersionedTag versionedTag1 = getVersionedTag(tag1), versionedTag2 = getVersionedTag(tag2);
				EdgeType edgeType = getTagEdgeType(tag1);
				int edgeOrdinal = getEdgeOrdinal(tag1);

				Node node1 = hashLookupTable.get(versionedTag1), node2 = hashLookupTable
						.get(versionedTag2);

				// Double check if tag1 and tag2 exist in the lookup file
				if (node1 == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(tag1)
								+ " is missed in graph lookup file!");
					}
				}
				if (node2 == null) {
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
				Edge e;

				String node1ModName = AnalysisUtil.getModuleName(node1), node2ModName = AnalysisUtil
						.getModuleName(node2);

				e = new Edge(node1, node2, edgeType, edgeOrdinal);

				if (existing == null) {
					// Be careful when dealing with the cross module nodes.
					// Cross-module edges are not added to any node, but the
					// edge from signature node to real entry node is preserved.
					// We onlly need to add the signature nodes to "nodes"
					node1.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
					if (isCoreModule(node2ModName)) {
						ModuleGraph moduleGraph = moduleGraphs
								.get(node2ModName);
						// Make sure the signature node is added
						moduleGraph.addSignatureNode(signatureHash);
						Node sigNode = moduleGraph.signature2Node
								.get(signatureHash);

						node2.setMetaNodeType(MetaNodeType.NORMAl);
						Edge sigEntryEdge = new Edge(sigNode, node2,
								EdgeType.CrossKernelModule, 0);
						sigNode.addOutgoingEdge(sigEntryEdge);
						node2.addIncomingEdge(sigEntryEdge);
					} else if (isCoreModule(node1ModName)) {
						// Make sure the signature node is added
						addSignatureNode(signatureHash);
						Node sigNode = signature2Node.get(signatureHash);

						e = new Edge(sigNode, node2,
								EdgeType.CrossKernelModule, 0);
						sigNode.addOutgoingEdge(e);
						node2.addIncomingEdge(e);
					} else {
						node1.addOutgoingEdge(e);
						node2.addIncomingEdge(e);
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

		if (fileIn != null) {
			try {
				fileIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Count how many wrong intra-module edges there are
	private int wrongIntraModuleEdgeCnt = 0;

	public void readIntraModuleEdges(ArrayList<String> intraModuleEdgeFiles,
			HashMap<VersionedTag, Node> hashLookupTable)
			throws InvalidTagException, TagNotFoundException,
			MultipleEdgeException {
		for (int i = 0; i < intraModuleEdgeFiles.size(); i++) {
			String intraModuleEdgeFile = intraModuleEdgeFiles.get(i);
			File file = new File(intraModuleEdgeFile);
			FileInputStream fileIn = null;
			ByteBuffer buffer = ByteBuffer.allocate(0x8);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			try {
				fileIn = new FileInputStream(file);
				FileChannel channel = fileIn.getChannel();

				while (true) {
					if (channel.read(buffer) < 0)
						break;
					buffer.flip();
					long tag1 = buffer.getLong();
					buffer.compact();

					channel.read(buffer);
					buffer.flip();
					long tag2 = buffer.getLong();
					buffer.compact();

					VersionedTag versionedTag1 = getVersionedTag(tag1), versionedTag2 = getVersionedTag(tag2);
					EdgeType edgeType = getTagEdgeType(tag1);
					int edgeOrdinal = getEdgeOrdinal(tag1);

					Node node1 = hashLookupTable.get(versionedTag1), node2 = hashLookupTable
							.get(versionedTag2);

					// Double check if tag1 and tag2 exist in the lookup file
					if (node1 == null) {
						if (DebugUtils.ThrowTagNotFound) {
							throw new TagNotFoundException("0x"
									+ Long.toHexString(tag1)
									+ " is missed in graph lookup file!");
						}
					}
					if (node2 == null) {
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

					// If one of the node locates in the "unknown" module,
					// simply discard those edges
					String node1ModName = AnalysisUtil.getModuleName(node1), node2ModName = AnalysisUtil
							.getModuleName(node2);
					if (node1ModName.equals("Unknown")
							|| node2ModName.equals("Unknown")) {
						continue;
					}

					if ((!node1ModName.equals(node2ModName))
							&& (isCoreModule(node1ModName) || isCoreModule(node2ModName))) {
						wrongIntraModuleEdgeCnt++;
						// Ignore those wrong edges at this point
						// System.out.println(node1 + "=>" + node2);
						continue;
					}

					Edge existing = node1.getOutgoingEdge(node2);
					Edge e = new Edge(node1, node2, edgeType, edgeOrdinal);
					if (existing == null) {
						node1.addOutgoingEdge(e);
						node2.addIncomingEdge(e);
					} else {
						if (!existing.equals(e)) {
							// One wired case to deal with here:
							// A call edge (direct) and a continuation edge can
							// point to the same block
							if ((existing.getEdgeType() == EdgeType.Direct && e.getEdgeType() == EdgeType.CallContinuation)
									|| (existing.getEdgeType() == EdgeType.CallContinuation && e.getEdgeType() == EdgeType.Direct)) {
								existing.setEdgeType(EdgeType.Direct);
							} else {
								String msg = "Multiple edges:\n" + "Edge1: "
										+ existing + "\n" + "Edge2: " + e;
								System.out.println(msg);
								if (DebugUtils.ThrowMultipleEdge) {
									throw new MultipleEdgeException(msg);
								}	
							}
						}
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {
				// System.out.println("Finish reading the file: " + fileName);
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Output the count for wrong edges if there is any
			if (wrongIntraModuleEdgeCnt > 0) {
				System.out.println("There are " + wrongIntraModuleEdgeCnt
						+ " cross-module edges in the intra-module edge file");
			}

			if (fileIn != null) {
				try {
					fileIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static ArrayList<ExecutionGraph> buildGraphsFromRunDir(String dir) {
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();

		File dirFile = new File(dir);
		String[] fileNames = dirFile.list();
		HashMap<Integer, ArrayList<String>> pid2LookupFiles = new HashMap<Integer, ArrayList<String>>(), pid2IntraModuleEdgeFiles = new HashMap<Integer, ArrayList<String>>();
		HashMap<Integer, String> pid2PairHashFile = new HashMap<Integer, String>(), pid2BlockHashFile = new HashMap<Integer, String>();
		HashMap<Integer, String> pid2ModuleFile = new HashMap<Integer, String>();
		HashMap<Integer, String> pid2CrossModuleEdgeFile = new HashMap<Integer, String>();

		for (int i = 0; i < fileNames.length; i++) {
			int pid = AnalysisUtil.getPidFromFileName(fileNames[i]);
			if (pid == 0)
				continue;
			if (pid2LookupFiles.get(pid) == null) {
				pid2LookupFiles.put(pid, new ArrayList<String>());
				pid2IntraModuleEdgeFiles.put(pid, new ArrayList<String>());
			}
			if (fileNames[i].indexOf("bb-graph-hash.") != -1) {
				pid2LookupFiles.get(pid).add(dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("bb-graph.") != -1) {
				pid2IntraModuleEdgeFiles.get(pid).add(dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("pair-hash") != -1) {
				pid2PairHashFile.put(pid, dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("block-hash") != -1) {
				pid2BlockHashFile.put(pid, dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf(".module.") != -1) {
				pid2ModuleFile.put(pid, dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("cross-module") != -1) {
				pid2CrossModuleEdgeFile.put(pid, dir + "/" + fileNames[i]);
			}
		}

		// Build the graphs
		for (int pid : pid2LookupFiles.keySet()) {

			ArrayList<String> lookupFiles = pid2LookupFiles.get(pid), intraModuleEdgeFiles = pid2IntraModuleEdgeFiles
					.get(pid);
			String crossModuleEdgeFile = pid2CrossModuleEdgeFile.get(pid), moduleFile = pid2ModuleFile
					.get(pid);
			if (lookupFiles.size() == 0)
				continue;
			String possibleProgName = AnalysisUtil.getProgName(lookupFiles
					.get(0));
			ExecutionGraph graph = new ExecutionGraph(intraModuleEdgeFiles,
					crossModuleEdgeFile, lookupFiles, moduleFile);
			graph.setRunDir(dir);

			// Initialize the relativeTag2Node hashtable
			// This is only used for debugging so far
			graph.normalizedTag2Node = new HashMap<NormalizedTag, Node>();
			for (int i = 0; i < graph.nodes.size(); i++) {
				Node n = graph.nodes.get(i);
				long relativeTag = AnalysisUtil.getRelativeTag(graph,
						n.getTag().tag);
				String moduleName = AnalysisUtil.getModuleName(graph,
						n.getTag().tag);
				graph.normalizedTag2Node.put(new NormalizedTag(moduleName,
						relativeTag), n);
			}

			// Initialize hash files and hash sets
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

	public HashSet<Node> getAccessibleNodes() {
		HashSet<Node> accessibleNodes = new HashSet<Node>();
		for (int i = 0; i < nodes.size(); i++) {
			nodes.get(i).resetVisited();
		}
		Queue<Node> bfsQueue = new LinkedList<Node>();
		for (long sigHash : signature2Node.keySet()) {
			bfsQueue.add(signature2Node.get(sigHash));
		}
		if (this instanceof ModuleGraph) {
			ModuleGraph mGraph = (ModuleGraph) this;
			if (mGraph.moduleName.startsWith("ntdll.dll-")) {
				bfsQueue.add(nodes.get(0));
			}
		}

		while (bfsQueue.size() > 0) {
			Node n = bfsQueue.remove();
			n.setVisited();
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
	 * This function is called when the graph is constructed. It can contain any
	 * kind of analysis of the current ExecutionGraph and output it the the
	 * console.
	 * 
	 * It could actually contains information like: 1. Number of nodes &
	 * signature nodes in the main module and in every module. 2. Number of
	 * non-kernel modules in the main module.
	 */
	public void analyzeGraph() {
		// Output basic nodes info
		int nodeCnt = nodes.size(), realNodeCnt = nodes.size()
				- signature2Node.size();
		System.out.println("Number of nodes in the main module: " + nodeCnt);
		System.out.println("Number of real nodes in the main module: "
				+ realNodeCnt);
		System.out.println("Number of signature nodes in the main module: "
				+ signature2Node.size());
		for (String name : moduleGraphs.keySet()) {
			ModuleGraph mGraph = moduleGraphs.get(name);
			nodeCnt += mGraph.nodes.size();
			realNodeCnt += mGraph.nodes.size() - mGraph.signature2Node.size();
		}
		System.out.println("Number of nodes in all module: " + nodeCnt);
		System.out
				.println("Number of real nodes in all module: " + realNodeCnt);

		// List how many non-core modules in main
		ArrayList<String> modulesInMain = new ArrayList<String>();
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			String modName = n.getNormalizedTag().moduleName;
			if (modName.equals("Unknown") || modName.equals("")) {
				continue;
			}
			if (!modulesInMain.contains(modName)) {
				modulesInMain.add(modName);
			}
		}
		for (int i = 0; i < modulesInMain.size(); i++) {
			System.out.print(modulesInMain.get(i) + "  ");
		}
		System.out.println();
		System.out
				.println("Number of modules in main: " + modulesInMain.size());

		// Count the subgraph with the most reachable nodes
		int maxCnt = 0;
		long maxSigHash = -1;
		for (long sigHash : signature2Node.keySet()) {
			Node sigNode = signature2Node.get(sigHash);
			for (int k = 0; k < sigNode.getOutgoingEdges().size(); k++) {
				Node realEntryNode = sigNode.getOutgoingEdges().get(k)
						.getToNode();

				Queue<Node> bfsQueue = new LinkedList<Node>();
				int cnt = 0;
				for (int i = 0; i < nodes.size(); i++) {
					nodes.get(i).resetVisited();
				}
				bfsQueue.add(realEntryNode);

				while (bfsQueue.size() > 0) {
					Node n = bfsQueue.remove();
					n.setVisited();
					cnt++;
					for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
						Node neighbor = n.getOutgoingEdges().get(i).getToNode();
						if (!neighbor.isVisited()) {
							bfsQueue.add(neighbor);
							neighbor.setVisited();
						}
					}
				}
				if (cnt > maxCnt) {
					maxCnt = cnt;
					maxSigHash = realEntryNode.getHash();
				}
			}
		}
		System.out.println(Long.toHexString(maxSigHash)
				+ " has the most reachable nodes (" + maxCnt + ").");
		HashSet<Node> accessibleNodes = getAccessibleNodes();
		System.out.println("Total reachable nodes: " + accessibleNodes.size());
		
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (!accessibleNodes.contains(n)) {
				System.out.println(n);
			}
		}
		
	}

	/**
	 * To validate the correctness of the graph. Basically it checks if entry
	 * points have no incoming edges, exit points have no outgoing edges. It
	 * might include more validation stuff later...
	 * 
	 * @return true means this is a valid graph, otherwise it's invalid
	 */
	public boolean validate() {

		// Check if the index of the node is correct
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (n.getIndex() != i) {
				System.out.println("Wrong index: " + n.getIndex());
				return false;
			}
		}

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