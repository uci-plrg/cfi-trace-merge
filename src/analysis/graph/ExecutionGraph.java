package analysis.graph;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

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

	private String tagFileName, lookupFileName;

	// nodes in an array in the read order from file
	private ArrayList<Node> nodes;
	
	private HashMap<Long, Node> hashLookupTable;

	public ExecutionGraph(String tagFileName, String lookupFileName) {
		this.tagFileName = tagFileName;
		this.lookupFileName = lookupFileName;
		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		init();
		System.out.println("Finish initializing the graph for: " + tagFileName);
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
						pw.println("node_" + Long.toHexString(nodes.get(i).hash) + "->"
								+ "node_" + Long.toHexString(node.hash) + "[label=\""
								+ edges.get(node) + "_" + Long.toHexString(node.tag) + "\"]");
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

	private void init() {
		readGraphLookup(lookupFileName);
		readGraph(tagFileName);
	}

	private void readGraphLookup(String fileName) {
		hashLookupTable = new HashMap<Long, Node>();
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;
		try {
			fileIn = new FileInputStream(fileName);
			dataIn = new DataInputStream(fileIn);
			long tag = 0, hash = 0;
			while (true) {
				// the tag and hash here is already a big-endian value
				tag = getTagEffectiveValue(AnalysisUtil
						.reverseForLittleEndian(dataIn.readLong()));
				hash = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());
//				System.out.println(Long.toHexString(tag));
//				System.out.println(Long.toHexString(hash));
				// FIXME
				// not sure if tags duplicate in lookup file
				// first assume that they don't
				// it seems that they don't duplicate in the first few runs
				if (hashLookupTable.containsKey(tag)) {
					System.out.println("Something's wrong??");
					return;
				}
				Node node = new Node(tag, hash);
				hashLookupTable.put(tag, node);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			// System.out.println("Finish reading the file: " + fileName);
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
	private void readGraph(String fileName) {
		File file = new File(fileName);
		// V <= E / 2 + 1
		int capacity = (int) file.length() / 16 / 2 + 1;
		nodes = null;
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;
		try {
			fileIn = new FileInputStream(file);
			dataIn = new DataInputStream(fileIn);
			nodes = new ArrayList<Node>(capacity);
			while (true) {
				long tag1 = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());
				int ordinal = getEdgeOrdinal(tag1);
				tag1 = getTagEffectiveValue(tag1);
				long tag2 = getTagEffectiveValue(AnalysisUtil
						.reverseForLittleEndian(dataIn.readLong()));
				Node node1 = hashLookupTable.get(tag1),
						node2 = hashLookupTable.get(tag2);

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

	// get the highest two byte of the tag, which represents the ordinal of the edge
	public static int getEdgeOrdinal(long tag) {
		//System.out.println(Long.toHexString(tag));
		return new Long(tag >> 48).intValue();
	}
	
	// get the lower 6 byte of the tag, which is a long integer
	public static long getTagEffectiveValue(long tag) {
		return tag << 16;
	}
}
