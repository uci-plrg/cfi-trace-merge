package analysis.graph;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import utils.AnalysisUtil;

public class ExecutionGraph {
	static public class Node {
		public long tag, hash;
		// Identify the same hash code with different tags
		public int hashOrdinal;

		public Node(long tag, long hash) {
			this.tag = tag;
			this.hash = hash;
			this.hashOrdinal = 0;
		}

		public Node(long tag, long hash, int hashOrdinal) {
			this.tag = tag;
			this.hash = hash;
			this.hashOrdinal = hashOrdinal;
		}

		public Node(long tag) {
			this.tag = tag;
		}

		/**
		 * In a single execution, tag is the only identifier for the node
		 */
		public boolean equals(Object o) {
			if (o.getClass() != Node.class) {
				return false;
			}
			Node node = (Node) o;
			if (node.tag != tag)
				return false;
			else
				return true;
		}

		public int hashCode() {
			return ((Long) tag).hashCode() << 5 ^ ((Long) hash).hashCode()
					^ hashOrdinal;
		}
	}

	// the edges of the graph comes with an ordinal
	private HashMap<Node, HashMap<Node, Integer>> adjacentList;

	private String runDirName;

	private String progName;

	// nodes in an array in the read order from file
	private ArrayList<Node> nodes;

	private HashMap<Long, Node> hashLookupTable;

	private HashSet<Long> blockHash;

	public void setBlockHash(String fileName) {
		blockHash = AnalysisUtil.getSetFromPath(fileName);
	}

	// if false, it means that the file doesn't exist or is in wrong format
	private boolean isValidGraph = true;

	public ExecutionGraph() {
		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
	}

	public ExecutionGraph(String tagFileName, String lookupFileName) {
		progName = AnalysisUtil.getProgName(tagFileName);

		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		init(tagFileName, lookupFileName);
		// outputFirstMain();
	}

	// public static ExecutionGraph buildGraphFromRunDir(String runDir) {
	// File dir = new File(runDir);
	// String tagFile = null,
	// lookupFile = null,
	// blockFile = null;
	// String progName = null;
	// for (File f : dir.listFiles()) {
	// if (f.getName().indexOf("bb-graph.") != -1) {
	// tagFile = f.getAbsolutePath();
	// if (progName == null)
	// progName = AnalysisUtil.getProgName(tagFile);
	// } else if (f.getName().indexOf("bb-graph-hash.") != -1) {
	// lookupFile = f.getAbsolutePath();
	// } else if (f.getName().indexOf("block-hash.") != -1) {
	// blockFile = f.getAbsolutePath();
	// }
	// }
	// ExecutionGraph graph = new ExecutionGraph(tagFile, lookupFile);
	// graph.setProgName(progName);
	// return graph;
	// }

	public String getProgName() {
		return progName;
	}

	private void setProgName(String progName) {
		this.progName = progName;
	}

	public static ExecutionGraph buildGraphFromRunDir(String runDir) {
		File dir = new File(runDir);
		String[] fileNames = dir.list();
		Arrays.sort(fileNames);

		// dealing with the file sorting and classifying
		ArrayList<String> tagFiles = new ArrayList<String>(), lookupFiles = new ArrayList<String>(), blockFiles = new ArrayList<String>();

		for (int i = 0; i < fileNames.length; i++) {
			if (fileNames[i].indexOf("bb-graph.") != -1) {
				tagFiles.add(runDir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("bb-graph-hash.") != -1) {
				lookupFiles.add(runDir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("block-hash.") != -1) {
				blockFiles.add(runDir + "/" + fileNames[i]);
			}
		}

		// generating the graph
		ExecutionGraph graph = new ExecutionGraph();
		graph.readGraphLookup(lookupFiles);
		graph.readGraph(tagFiles);

		return graph;
	}

	/**
	 * this method is for programs that will fork and exec other programs
	 * 
	 * @param otherGraph
	 * @return
	 */
	ExecutionGraph mergeTwoGraphs(ExecutionGraph otherGraph) {
		ExecutionGraph newGraph = new ExecutionGraph();
		newGraph.adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		return null;
	}

	/**
	 * try to merge another graph with itself !!! Seems that every two graphs
	 * can be merged, so maybe there should be a way to evaluate how much the
	 * two graphs conflict One case is unmergeable: two direct branch nodes with
	 * same hash value but have different branch targets (Seems wired!!)
	 * 
	 * ####42696542a8bb5822 I am doing a trick here: programs in x86/linux seems
	 * to enter their main function after a very similar dynamic-loading
	 * process, at the end of which there is a indirect branch which jumps to
	 * the real main blocks. In the environment of this machine, the hash value
	 * of that 'final block' is 0x1d84443b9bf8a6b3. ####
	 * 
	 * FIXME Something's wrong here, the block that finally jumps to main is
	 * 0x4f1f7a5c30ae8622, and the previously found node is actually from the
	 * constructor of the program (__libc_csu_init). Things might get wrong
	 * here!!!
	 * 
	 * @param otherGraph
	 */

	private long specialHash = new BigInteger("4f1f7a5c30ae8622", 16)
			.longValue();

	public ExecutionGraph mergeGraph(ExecutionGraph otherGraph) {
		return null;

	}

	public long outputFirstMain() {
		Node n = null;
		long firstMainHash = 0;
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).hash == specialHash) {
				n = nodes.get(i);
				if (adjacentList.get(n).size() > 1) {
					System.out.println("More than one target!");
					return -1;
				} else {
					for (Node node : adjacentList.get(n).keySet()) {
						firstMainHash = node.hash;
						// System.out.println(Long.toHexString(firstMainHash));
					}
				}
				break;
			}
		}
		return firstMainHash;
	}

	public void dumpGraph(String fileName) {
		File file = new File(fileName);
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		PrintWriter pw = null;
		try {
			pw = new PrintWriter(fileName);
			pw.println("digraph runGraph {");
			long firstMainBlock = outputFirstMain();
			pw.println("# First main block: "
					+ Long.toHexString(firstMainBlock));
			for (int i = 0; i < nodes.size(); i++) {
				// pw.println("node_" + Long.toHexString(nodes.get(i).hash));
				pw.println("node_" + Long.toHexString(nodes.get(i).tag)
						+ "[label=\"" + Long.toHexString(nodes.get(i).hash)
						+ "\"]");

				HashMap<Node, Integer> edges = adjacentList.get(nodes.get(i));
				for (Node node : edges.keySet()) {
					int flag = edges.get(node);
					int ordinal = flag % 256;
					String branchType;
					if (flag / 256 == 1) {
						branchType = "d";
					} else {
						branchType = "i";
					}
					// pw.println("node_" + Long.toHexString(nodes.get(i).hash)
					// + "->"
					// + "node_" + Long.toHexString(node.hash) + "[label=\""
					// + branchType + "_" + ordinal + "_" +
					// Long.toHexString(node.tag) + "\"]");
					pw.println("node_" + Long.toHexString(nodes.get(i).tag)
							+ "->" + "node_" + Long.toHexString(node.tag)
							+ "[label=\"" + branchType + "_" + ordinal + "\"]");

				}
			}

			pw.print("}");
			pw.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (pw != null)
			pw.close();
	}

	private void init(String tagFileName, String lookupFileName) {
		// try {
		readGraphLookup(lookupFileName);
		readGraph(tagFileName);
		// } catch (NullPointerException e) {
		// isValidGraph = false;
		// }
	}

	public boolean isValidGraph() {
		return this.isValidGraph;
	}

	private void readGraphLookup(ArrayList<String> lookupFiles) {
		hashLookupTable = new HashMap<Long, Node>();
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;

		for (int i = 0; i < lookupFiles.size(); i++) {
			String lookupFile = lookupFiles.get(i);
			if (lookupFile.indexOf("ld") == -1)
				continue;
			try {
				fileIn = new FileInputStream(lookupFiles.get(i));
				dataIn = new DataInputStream(fileIn);
				long tag = 0, hash = 0;
				while (true) {
					// the tag and hash here is already a big-endian value
					long tagOriginal = AnalysisUtil
							.reverseForLittleEndian(dataIn.readLong());
					tag = getTagEffectiveValue(tagOriginal);

					if (tagOriginal != tag) {
						System.out.println("Tag more than 6 bytes");
						System.out.println(Long.toHexString(tagOriginal)
								+ " : " + Long.toHexString(tag));
					}

					hash = AnalysisUtil.reverseForLittleEndian(dataIn
							.readLong());
					// FIXME
					// not sure if tags duplicate in lookup file
					// first assume that they don't
					// it seems that they don't duplicate in the first few runs
					if (hashLookupTable.containsKey(tag)) {
						if (hashLookupTable.get(tag).hash != hash) {
							isValidGraph = false;
							System.out
									.println(Long.toHexString(tag)
											+ " -> "
											+ Long.toHexString(hashLookupTable
													.get(tag).hash) + ":"
											+ Long.toHexString(hash) + "  " + lookupFile);
						}
					}
					Node node = new Node(tag, hash);
					hashLookupTable.put(tag, node);
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
	}

	private void readGraphLookup(String fileName) throws NullPointerException {
		hashLookupTable = new HashMap<Long, Node>();
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;

		try {
			fileIn = new FileInputStream(fileName);
			dataIn = new DataInputStream(fileIn);
			long tag = 0, hash = 0;
			while (true) {
				// the tag and hash here is already a big-endian value
				long tagOriginal = AnalysisUtil.reverseForLittleEndian(dataIn
						.readLong());

				tag = getTagEffectiveValue(tagOriginal);

				if (tagOriginal != tag) {
					System.out.println("Tag more than 6 bytes");
					System.out.println(Long.toHexString(tagOriginal) + " : "
							+ Long.toHexString(tag));
				}
				hash = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());

				// if (blockHash.contains(tag)) {
				// System.out.println("contains tag: " + Long.toHexString(tag));
				// } else if (!blockHash.contains(hash)) {
				// System.out.println("not contain hash: " +
				// Long.toHexString(hash));
				// }
				// if (hash >>> 40 == 0x7f || hash >>> 24 == 0x71 || hash >>> 16
				// == 0x40) {
				// System.out.println(Long.toHexString(hash));
				// }
				// FIXME
				// not sure if tags duplicate in lookup file
				// first assume that they don't
				// it seems that they don't duplicate in the first few runs
				if (hashLookupTable.containsKey(tag)) {
					if (hashLookupTable.get(tag).hash != hash) {
						isValidGraph = false;
						System.out
								.println(Long.toHexString(tag)
										+ " -> "
										+ Long.toHexString(hashLookupTable
												.get(tag).hash) + ":"
										+ Long.toHexString(hash));
					}
				}
				Node node = new Node(tag, hash);
				hashLookupTable.put(tag, node);
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

	public void readGraph(ArrayList<String> tagFiles)
			throws NullPointerException {
		for (int i = 0; i < tagFiles.size(); i++) {
			String tagFile = tagFiles.get(i);
			if (tagFile.indexOf("ld") == -1)
				continue;
			File file = new File(tagFile);
			// V <= E / 2 + 1
			int capacity = (int) file.length() / 16 / 2 + 1;
			nodes = null;
			FileInputStream fileIn = null;
			DataInputStream dataIn = null;
			// to track how many tags does not exist in lookup file
			HashSet<Long> hashesNotInLookup = new HashSet<Long>();
			try {
				fileIn = new FileInputStream(file);
				dataIn = new DataInputStream(fileIn);
				nodes = new ArrayList<Node>(capacity);
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
						// System.out.println(Long.toHexString(tag2Original) +
						// " : "
						// + Long.toHexString(tag2));
					}

					Node node1 = hashLookupTable.get(tag1), node2 = hashLookupTable
							.get(tag2);
					// double check if tag1 and tag2 exist in the lookup file
					if (node1 == null) {
						// System.out.println(Long.toHexString(tag1) +
						// " is not in lookup file");
						hashesNotInLookup.add(tag1);
					}
					if (node2 == null) {
						// System.out.println(Long.toHexString(tag2) +
						// " is not in lookup file");
						hashesNotInLookup.add(tag2);
					}
					if (node1 == null || node2 == null)
						continue;

					// also put the nodes into the adjacentList if they are not
					// stored yet
					// add node to an array, which is in their seen order in the
					// file
					if (!adjacentList.containsKey(node1)) {
						adjacentList.put(node1, new HashMap<Node, Integer>());
						nodes.add(node1);
					}
					if (!adjacentList.containsKey(node2)) {
						adjacentList.put(node2, new HashMap<Node, Integer>());
						nodes.add(node2);
					}

					HashMap<Node, Integer> edges;
					edges = adjacentList.get(node1);
					if (!edges.containsKey(node2))
						edges.put(node2, flag);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {
				// System.out.println("Finish reading the file: " + fileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (hashesNotInLookup.size() != 0) {
				isValidGraph = false;
				System.out.println(hashesNotInLookup.size()
						+ " tag doesn't exist in lookup file -> " + tagFile);
				// for (long l : hashesNotInLookup) {
				// System.out.println(Long.toHexString(l));
				// }
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

	/**
	 * 
	 * @param fileName
	 * @return an ArrayList of tag seen in order (order in the file)
	 */
	public void readGraph(String fileName) throws NullPointerException {
		File file = new File(fileName);
		// V <= E / 2 + 1
		int capacity = (int) file.length() / 16 / 2 + 1;
		nodes = null;
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;
		// to track how many tags does not exist in lookup file
		HashSet<Long> hashesNotInLookup = new HashSet<Long>();
		try {
			fileIn = new FileInputStream(file);
			dataIn = new DataInputStream(fileIn);
			nodes = new ArrayList<Node>(capacity);
			while (true) {
				long tag1 = AnalysisUtil.reverseForLittleEndian(dataIn
						.readLong());
				int flag = getEdgeFlag(tag1);
				tag1 = getTagEffectiveValue(tag1);
				long tag2Original = AnalysisUtil.reverseForLittleEndian(dataIn
						.readLong());
				long tag2 = getTagEffectiveValue(tag2Original);
				if (tag2 != tag2Original) {
					System.out.println("Something wrong about the tag");
					// System.out.println(Long.toHexString(tag2Original) + " : "
					// + Long.toHexString(tag2));
				}

				Node node1 = hashLookupTable.get(tag1), node2 = hashLookupTable
						.get(tag2);
				// double check if tag1 and tag2 exist in the lookup file
				if (node1 == null) {
					// System.out.println(Long.toHexString(tag1) +
					// " is not in lookup file");
					hashesNotInLookup.add(tag1);
				}
				if (node2 == null) {
					// System.out.println(Long.toHexString(tag2) +
					// " is not in lookup file");
					hashesNotInLookup.add(tag2);
				}
				if (node1 == null || node2 == null)
					continue;

				// also put the nodes into the adjacentList if they are not
				// stored yet
				// add node to an array, which is in their seen order in the
				// file
				if (!adjacentList.containsKey(node1)) {
					adjacentList.put(node1, new HashMap<Node, Integer>());
					nodes.add(node1);
				}
				if (!adjacentList.containsKey(node2)) {
					adjacentList.put(node2, new HashMap<Node, Integer>());
					nodes.add(node2);
				}

				HashMap<Node, Integer> edges;
				edges = adjacentList.get(node1);
				if (!edges.containsKey(node2))
					edges.put(node2, flag);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			// System.out.println("Finish reading the file: " + fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (hashesNotInLookup.size() != 0) {
			isValidGraph = false;
			System.out.println(hashesNotInLookup.size()
					+ " tag doesn't exist in lookup file -> " + fileName);
			// for (long l : hashesNotInLookup) {
			// System.out.println(Long.toHexString(l));
			// }
		}

		if (dataIn != null) {
			try {
				dataIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		// since V will never change once the graph is created
		nodes.trimToSize();

	}

	// eturn the highest two bytes
	public static int getEdgeFlag(long tag) {
		return new Long(tag >>> 48).intValue();
	}

	public static boolean isDirectBranch(long tag) {
		int flag = new Long(tag >>> 48).intValue();
		if (flag / 256 == 1) {
			return true;
		} else {
			return false;
		}
	}

	// get the lower 6 byte of the tag, which is a long integer
	public static long getTagEffectiveValue(long tag) {
		// System.out.print(Long.toHexString(tag));
		Long res = tag << 16 >>> 16;
		// System.out.println(":" + Long.toHexString(res));
		// System.out.println(Long.toHexString(res));
		// if (res >>> 56 != 0x7f)
		// res = res << 36 >>> 36;
		// else
		// res = res << 36 >>> 36 | 0x7fl << 56;
		// System.out.println(Long.toHexString(res));
		return res;
	}

	public static void main(String[] argvs) {
		// ArrayList<ExecutionGraph> graphs =
		// buildGraphsFromRunDir("./grouping-analysis");
		// ArrayList<ExecutionGraph> graphs =
		// buildGraphsFromRunDirAnotherVersion(argvs[0]);
		ExecutionGraph graph = buildGraphFromRunDir(argvs[0]);
		
		if (!graph.isValidGraph()) {
			System.out.print("This is a wrong graph!");
		}
		long l = graph.outputFirstMain();
		graph.dumpGraph("graph-files/tmp.dot");

	}
}
