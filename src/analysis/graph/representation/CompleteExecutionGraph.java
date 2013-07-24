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

import utils.AnalysisUtil;

import analysis.exception.graph.InvalidTagException;
import analysis.exception.graph.MultipleEdgeException;
import analysis.exception.graph.OverlapModuleException;
import analysis.exception.graph.TagNotFoundException;
import analysis.graph.debug.DebugUtils;

public class CompleteExecutionGraph extends ExecutionGraph {

	public CompleteExecutionGraph(ArrayList<String> intraModuleEdgeFiles,
			String crossModuleEdgeFile, ArrayList<String> lookupFiles,
			String moduleFile) {
		nodes = new ArrayList<Node>();
		hash2Nodes = new NodeHashMap();
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
		HashMap<Long, Node> hashLookupTable;
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

		validate();
		if (!isValidGraph) {
			System.out.println("Pid " + pid + " is not a valid graph!");
		}
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

					// Be careful about the index here!!!
					// If the node is in a core module, the addModuleNode
					// function should fix the index.
					Node node = new Node(this, tag, hash, nodes.size(),
							metaNodeType);
					nodes.add(node);
					// Add it the the hash2Nodes mapping
					hash2Nodes.add(node);
					hashLookupTable.put(tag, node);
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

	public void readCrossModuleEdges(String crossModuleEdgeFile,
			HashMap<Long, Node> hashLookupTable) throws MultipleEdgeException,
			InvalidTagException, TagNotFoundException {
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
				int flags = getEdgeFlag(tag1);
				tag1 = getTagEffectiveValue(tag1);

				channel.read(buffer);
				buffer.flip();
				long tag2 = buffer.getLong();
				buffer.compact();

				channel.read(buffer);
				buffer.flip();
				long signitureHash = buffer.getLong();
				buffer.compact();

				Node node1 = hashLookupTable.get(tag1), node2 = hashLookupTable
						.get(tag2);

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

				e = new Edge(node1, node2, flags);

				if (existing == null) {
					// Be careful when dealing with the cross module nodes
					// Cross-module edges are not added to any node, but the
					// edge from signature node to real entry node is preserved
					node1.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
					node1.addOutgoingEdge(e);
					node2.addIncomingEdge(e);
				} else if (!existing.equals(e)) {
					String msg = "Multiple cross module edges:\n"
							+ "Existing edge: " + e + "\n" + "New edge: "
							+ existing + "\n";
					if (DebugUtils.ThrowMultipleEdge) {
						throw new MultipleEdgeException(msg);
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

	public void readIntraModuleEdges(ArrayList<String> intraModuleEdgeFiles,
			HashMap<Long, Node> hashLookupTable) throws InvalidTagException,
			TagNotFoundException, MultipleEdgeException {
		for (int i = 0; i < intraModuleEdgeFiles.size(); i++) {
			String intraModuleEdgeFile = intraModuleEdgeFiles.get(i);
			File file = new File(intraModuleEdgeFile);
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

	public static ArrayList<CompleteExecutionGraph> buildCompleteGraphsFromRunDir(
			String dir) {
		ArrayList<CompleteExecutionGraph> graphs = new ArrayList<CompleteExecutionGraph>();

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
			CompleteExecutionGraph graph = new CompleteExecutionGraph(
					intraModuleEdgeFiles, crossModuleEdgeFile, lookupFiles,
					moduleFile);

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
}
