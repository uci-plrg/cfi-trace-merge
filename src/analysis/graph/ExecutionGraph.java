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

		public Node(long tag, long hash) {
			this.tag = tag;
			this.hash = hash;
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
			return ((Long) tag).hashCode() << 5 ^ ((Long) hash).hashCode();
		}
	}

	// the edges of the graph comes with an ordinal
	private HashMap<Node, HashMap<Node, Integer>> adjacentList;
	
	private String runDirName;
	
	private HashSet<Long> blockSet;

	// nodes in an array in the read order from file
	private ArrayList<Node> nodes;
	
	private HashMap<Long, Node> hashLookupTable;
	
	// if false, it means that the file doesn't exist or is in wrong format
	private boolean isValidGraph = true;

	public ExecutionGraph(String tagFileName, String lookupFileName, String blockFile) {
		if (blockFile != null) {
			blockSet = AnalysisUtil.getSetFromPath(blockFile);
		}
		
		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		init(tagFileName, lookupFileName);
		//outputFirstMain();
	}
	
	public ExecutionGraph(String tagFileName, String lookupFileName) {
		this(tagFileName, lookupFileName, null);
	}
	
	public static ExecutionGraph buildGraphFromRunDir(String runDir) {
		File dir = new File(runDir);
		String tagFile = null,
			lookupFile = null,
			blockFile = null;
		for (File f : dir.listFiles()) {
			if (f.getName().indexOf("bb-graph.") != -1) {
				tagFile = f.getAbsolutePath();
			} else if (f.getName().indexOf("bb-graph-hash.") != -1) {
				lookupFile = f.getAbsolutePath();
			} else if (f.getName().indexOf("block-hash.") != -1) {
				blockFile = f.getAbsolutePath();
			}
		}
		return new ExecutionGraph(tagFile, lookupFile, blockFile);
	}
	
	public static ArrayList<ExecutionGraph> buildGraphsFromRunDir(String runDir) {
		File dir = new File(runDir);
		String[] fileNames = dir.list();
		Arrays.sort(fileNames);
		
		// dealing with the file sorting and classifying
		HashMap<String, ArrayList<String>> progName2TagFiles = new HashMap<String, ArrayList<String>>(),
			progName2LookupFiles = new HashMap<String, ArrayList<String>>(),
			progName2BlockFiles = new HashMap<String, ArrayList<String>>();
		
		for (int i = 0; i < fileNames.length; i++) {
			String progName = AnalysisUtil.getProgName(fileNames[i]);
			if (progName == null)
				continue;
			if (!progName2TagFiles.containsKey(progName)) {
				progName2TagFiles.put(progName, new ArrayList<String>());
				progName2LookupFiles.put(progName, new ArrayList<String>());
				progName2BlockFiles.put(progName, new ArrayList<String>());
			}
			if (fileNames[i].indexOf("bb-graph.") != -1) {
				progName2TagFiles.get(progName).add(runDir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("bb-graph-hash.") != -1) {
				progName2LookupFiles.get(progName).add(runDir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("block-hash.") != -1) {
				progName2BlockFiles.get(progName).add(runDir + "/" + fileNames[i]);
			}
		}
		
		// generating the graphs
		
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();
		ArrayList<String> invalidFiles = new ArrayList<String>(); 
		for (String progName : progName2TagFiles.keySet()) {
//			if (progName.equals("dash"))
//				continue;
			ArrayList<String> tagFiles = progName2TagFiles.get(progName),
				lookupFiles = progName2LookupFiles.get(progName),
				blockFiles = progName2BlockFiles.get(progName);
			System.out.println(tagFiles.size());
			for (int i = 0; i < tagFiles.size(); i++) {
				ExecutionGraph graph;
				if (i > blockFiles.size() - 1)
					graph = new ExecutionGraph(tagFiles.get(i), lookupFiles.get(i), null);
				else
					graph = new ExecutionGraph(tagFiles.get(i), lookupFiles.get(i), blockFiles.get(i));
				if (!graph.isValidGraph)
					invalidFiles.add(tagFiles.get(i));
				graphs.add(graph);
			}
		}
		
		for (int i = 0; i < invalidFiles.size(); i++)
			System.out.println(invalidFiles.get(i));
		return graphs;
	}
	
	/**
	 * this method is for programs that will fork and exec other programs
	 * @param otherGraph
	 * @return
	 */
	private void mergeGraphFromSameRun(ExecutionGraph otherGraph) {
		for (Node n : otherGraph.adjacentList.keySet()) {
			if (adjacentList.containsKey(n)) {
				
			}
		}
	}
	
	/**
	 * try to merge another graph with itself
	 * !!! Seems that every two graphs can be merged,
	 * so maybe there should be a way to evaluate how
	 * much the two graphs conflict
	 * One case is unmergeable: two direct branch nodes with same
	 * hash value but have different branch targets (Seems wired!!)
	 * 
	 * ####42696542a8bb5822
	 * I am doing a trick here: programs in x86/linux seems to enter
	 * their main function after a very similar dynamic-loading process,
	 * at the end of which there is a indirect branch which jumps to the
	 * real main blocks. In the environment of this machine, the hash value
	 * of that 'final block' is 0x1d84443b9bf8a6b3. 
	 * ####
	 * 
	 * FIXME
	 * Something's wrong here, the block that finally jumps to main is
	 * 0x4f1f7a5c30ae8622, and the previously found node is actually from
	 * the constructor of the program (__libc_csu_init).
	 * Things might get wrong here!!!
	 * 
	 * @param otherGraph
	 */
	
	private long specialHash = new BigInteger("4f1f7a5c30ae8622", 16).longValue();
	
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
					return 0;
				} else {
					for (Node node : adjacentList.get(n).keySet()) {
						firstMainHash = node.hash;
//						System.out.println(Long.toHexString(firstMainHash));
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
			for (int i = 0; i < nodes.size(); i++) {
				//pw.println("node_" + Long.toHexString(nodes.get(i).hash));
				pw.println("node_" + Long.toHexString(nodes.get(i).hash) + "[label=\""
						+ Long.toHexString(nodes.get(i).tag) + "\"]");
				
				HashMap<Node, Integer> edges = adjacentList.get(nodes.get(i));
				for (Node node : edges.keySet()) {
					int flag = edges.get(node);
					int ordinal = flag % 256;
					String branchType;
					if (flag / 256 == 1) {
						branchType = "direct";
					} else {
						branchType = "indirect";
					}
					pw.println("node_" + Long.toHexString(nodes.get(i).hash) + "->"
							+ "node_" + Long.toHexString(node.hash) + "[label=\""
							+ branchType + "_" + ordinal + "_" + Long.toHexString(node.tag) + "\"]");
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
//		try {
			readGraphLookup(lookupFileName);
			readGraph(tagFileName);
//		} catch (NullPointerException e) {
//			isValidGraph = false;
//		}
	}
	
	public boolean isValidGraph() {
		return this.isValidGraph;
	}

	private void readGraphLookup(String fileName) throws NullPointerException {
		hashLookupTable = new HashMap<Long, Node>();
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;
		int count = 0;
		try {
			fileIn = new FileInputStream(fileName);
			dataIn = new DataInputStream(fileIn);
			long tag = 0, hash = 0;
			while (true) {
				// the tag and hash here is already a big-endian value
				long tagOriginal = AnalysisUtil
						.reverseForLittleEndian(dataIn.readLong());
				count++;
				tag = getTagEffectiveValue(tagOriginal);
				//System.out.println(Long.toHexString(tagOriginal) + " : " + Long.toHexString(tag));
				if (tagOriginal != tag) {
					System.out.println("Tag more than 6 bytes");
					System.out.println(Long.toHexString(tagOriginal) + " : " + Long.toHexString(tag));
				}
				hash = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());
				count++;
				if (blockSet != null && !blockSet.contains(hash)) {
					System.out.println("DR broke!!! Hash not in block hash file!");
				}
//				System.out.println(Long.toHexString(tag));
//				System.out.println(Long.toHexString(hash));
				// FIXME
				// not sure if tags duplicate in lookup file
				// first assume that they don't
				// it seems that they don't duplicate in the first few runs
				if (hashLookupTable.containsKey(tag)) {
					if (hashLookupTable.get(tag).hash != hash) {
						isValidGraph = false;
						//System.out.println("Something's wrong ----> invalid graph??");
						System.out.println(Long.toHexString(tag) + " -> " + Long.toHexString(hashLookupTable.get(tag).hash)
								+ ":" + Long.toHexString(hash));
						//return;						
					}
//					System.out.println("Something's wrong ----> invalid graph??");
//					return;
					
//					System.out.println("Something's wrong??");
//					if (hashLookupTable.get(tag).hash != hash) {
//						System.out.println("Something's really wrong??");
//						return;
//					}
				}
				Node node = new Node(tag, hash);
				hashLookupTable.put(tag, node);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			
			System.out.println("Finish reading the file: " + fileName + " " + count);
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

	/**
	 * 
	 * @param fileName
	 * @return an ArrayList of tag seen in order (order in the file)
	 */
	private void readGraph(String fileName) throws NullPointerException {
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
				long tag1 = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());
				int ordinal = getEdgeOrdinal(tag1);
				tag1 = getTagEffectiveValue(tag1);
				long tag2Original = AnalysisUtil
						.reverseForLittleEndian(dataIn.readLong());
				long tag2 = getTagEffectiveValue(tag2Original);
				if (tag2 != tag2Original) {
//					System.out.println("Something wrong about reading the graph");
//					System.out.println(Long.toHexString(tag2Original) + " : " + Long.toHexString(tag2));
				}
					
				Node node1 = hashLookupTable.get(tag1),
						node2 = hashLookupTable.get(tag2);
				// double check if tag1 and tag2 exist in the lookup file
				if (node1 == null) {
					//System.out.println(Long.toHexString(tag1) + " is not in lookup file");
					hashesNotInLookup.add(tag1);
				}
				if (node2 == null) {
					//System.out.println(Long.toHexString(tag2) + " is not in lookup file");
					hashesNotInLookup.add(tag2);
				}
				if (node1 == null || node2 == null)
					continue;

				// also put the nodes into the adjacentList if they are not stored yet
				// add node to an array, which is in their seen order in the file
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
					edges.put(node2, ordinal);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			// System.out.println("Finish reading the file: " + fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (hashesNotInLookup.size() != 0)
			System.out.println(hashesNotInLookup.size() + " tag doesn't exist in lookup file -> " + fileName);
		
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

	// get the second highest byte of the tag,
	// which represents the ordinal of the edge
	// !!! At this point, just return the highest two bytes
	public static int getEdgeOrdinal(long tag) {
		Long res = tag >>> 48;
		return res.intValue();
//		int flag = new Long(tag >>> 48).intValue();
//		return flag % 256;
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
		//System.out.print(Long.toHexString(tag));
		Long res = tag << 16 >>> 16;
		//System.out.println(":" + Long.toHexString(res));
//		System.out.println(Long.toHexString(res));
//		if (res >>> 56 != 0x7f)
//			res = res << 36 >>> 36;
//		else
//			res = res << 36 >>> 36 | 0x7fl << 56;
//		System.out.println(Long.toHexString(res));
		return res;
	}
	
	public static void main(String[] argvs) {
		//ArrayList<ExecutionGraph> graphs = buildGraphsFromRunDir("./grouping-analysis");
		ArrayList<ExecutionGraph> graphs = buildGraphsFromRunDir(argvs[0]);
		for (int i = 0; i < graphs.size(); i++) {
			ExecutionGraph g = graphs.get(i);
			long l = graphs.get(i).outputFirstMain();
			System.out.println();
		}
		
	}
}
