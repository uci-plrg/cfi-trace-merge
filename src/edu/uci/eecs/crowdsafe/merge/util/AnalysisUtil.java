package edu.uci.eecs.crowdsafe.merge.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;

public class AnalysisUtil {
	public static final ByteOrder byteOrder = ByteOrder.nativeOrder();

	// In format of "tar.bb-graph-hash.2013-03-06.06-44-36.4947-4947.dat"
	public static int getPidFromFile(File file) {
		String filename = file.getName();
		int lastDashPos = filename.lastIndexOf('-');
		int lastDotPos = filename.lastIndexOf('.');
		return Integer.parseInt(filename.substring(lastDashPos + 1, lastDotPos));
	}

	private static void findHashFiles(File dir, ArrayList<String> lists) {
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				findHashFiles(f, lists);
			} else if (f.getName().indexOf("pair-hash") != -1) {
				lists.add(f.getAbsolutePath());
			}
		}
	}

	public static ArrayList<String> getAllHashFiles(String dir) {
		ArrayList<String> hashFiles = new ArrayList<String>();
		File dirFile = new File(dir);
		findHashFiles(dirFile, hashFiles);
		return hashFiles;
	}

	private static void findRunDirs(File dir, ArrayList<String> lists) {
		for (File f : dir.listFiles()) {
			if (f.isDirectory() && f.getName().indexOf("run") == -1) {
				findRunDirs(f, lists);
			} else if (f.isDirectory() && f.getName().indexOf("run") != -1) {
				lists.add(f.getAbsolutePath());
			}
		}
	}

	public static ArrayList<String> getAllRunDirs(String dir) {
		ArrayList<String> runDirs = new ArrayList<String>();
		File rootDir = new File(dir);
		findRunDirs(rootDir, runDirs);
		return runDirs;
	}

	public static ArrayList<String> getStringPerline(String filename) {
		ArrayList<String> list = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String str = null;
			while ((str = br.readLine()) != null) {
				list.add(str);
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return list;
	}

	public static void saveStringPerline(String filename, ArrayList<String> list, boolean append) {
		try {
			PrintWriter pw = new PrintWriter(new FileOutputStream(filename, append));
			for (String str : list) {
				pw.println(str);
			}
			pw.flush();
			pw.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static Set<Long> minus(Set<Long> s1, Set<Long> s2) {
		Set<Long> res = new HashSet<Long>(s1);
		for (Long elem : s2)
			if (res.contains(elem))
				res.remove(elem);
		return res;
	}

	public static Set<Long> union(Set<Long> s1, Set<Long> s2) {
		Set<Long> res = new HashSet<Long>(s1);
		res.addAll(s2);
		return res;
	}

	public static Set<Long> intersection(Set<Long> s1, Set<Long> s2) {
		Set<Long> res = new HashSet<Long>(s1);
		res.retainAll(s2);
		return res;
	}

	public static Set<Long> mergeSet(Set<Long>... sets) {
		Set<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < sets.length; i++) {
			resSet.addAll(sets[i]);
		}
		return resSet;
	}

	public static Set<Long> mergeSet(File... hashFiles) {
		Set<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < hashFiles.length; i++) {
			resSet.addAll(loadHashFile(hashFiles[i]));
		}
		return resSet;
	}

	public static List<Long> loadHashFile(File hashFile) {
		FileInputStream in = null;
		FileChannel channel = null;
		try {
			in = new FileInputStream(hashFile);
			channel = in.getChannel();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ByteBuffer buffer = ByteBuffer.allocate(0x8);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		ArrayList<Long> allInstances = new ArrayList<Long>();

		try {
			// int bytesLeft = in.available() / 8;
			Long hashCode;
			while (true) {
				if (channel.read(buffer) < 0)
					break;
				buffer.flip();
				hashCode = buffer.getLong();
				buffer.compact();
				allInstances.add(hashCode);
			}
		} catch (EOFException e) {
			// end of line
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return allInstances;
	}

	public static Set<Long> loadHashSet(File hashFile) {
		FileInputStream in = null;
		FileChannel channel = null;
		try {
			in = new FileInputStream(hashFile);
			channel = in.getChannel();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ByteBuffer buffer = ByteBuffer.allocate(0x8);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		HashSet<Long> set = new HashSet<Long>();

		try {
			// int bytesLeft = in.available() / 8;
			Long hashCode;
			while (true) {
				if (channel.read(buffer) < 0)
					break;
				buffer.flip();
				hashCode = buffer.getLong();
				buffer.compact();
				set.add(hashCode);
			}
		} catch (EOFException e) {
			// end of line
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return set;
	}

	public static void writeSetToFile(File outputFile, Set<Long> hashset) {
		try {
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}

			FileOutputStream outputStream = new FileOutputStream(outputFile, false);
			DataOutputStream dataOutput = new DataOutputStream(outputStream);

			Log.log("Start outputting hash set to " + outputFile.getAbsolutePath() + " file.");

			Log.log("Finish outputting hash set to " + outputFile.getAbsolutePath() + " file.");
			outputStream.close();
			dataOutput.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Take advantage of the relative tag to filter out the immediate address
	 * 
	 * @param left
	 * @param right
	 */

	public static void filteroutImmeAddr(ModuleGraphCluster left, ModuleGraphCluster right) {
		int modificationCnt = 0;

		PrintWriter pw = null;
		if (DebugUtils.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
			try {
				String fileName = left.getGraphData().containingGraph.dataSource.getProcessName() + ".imme-addr."
						+ left.getGraphData().containingGraph.dataSource.getProcessId() + "-"
						+ right.getGraphData().containingGraph.dataSource.getProcessId() + ".txt";
				String absolutePath = DebugUtils.MODIFIED_HASH_DIR + "/" + fileName;
				File f = new File(DebugUtils.MODIFIED_HASH_DIR);
				f.mkdirs();
				pw = new PrintWriter(absolutePath);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

		for (ExecutionNode leftNode : left.getGraphData().nodesByKey.values()) {
			if (leftNode.getTagVersion() > 0)
				continue; // can't be sure about these re-written things
			if (leftNode.getKey().module.unit.isDynamic())
				continue; // make no assumptions about these
			ExecutionNode rightNode = right.getGraphData().nodesByKey.get(leftNode.getKey());
			if (rightNode == null) {
				continue;
			}
			if (leftNode.getHash() != rightNode.getHash()) {
				// replace the right node with a copy having the left node's hash
				right.getGraphData().nodesByKey.put(rightNode.getKey(), rightNode.changeHashCode(leftNode.getHash()));
				modificationCnt++;
				if (DebugUtils.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
					pw.println("leftNode: " + leftNode);
					pw.println("rightNode: " + rightNode);
				}
			}
		}
		if (DebugUtils.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
			pw.flush();
			pw.close();
		}

		Log.log("Total number of hash modification: " + modificationCnt);
	}

	/**
	 * This function is used to cheat when merging two executions from the same program. It will use the relative tag to
	 * verify if this is a correct match. n1 is allowed to be null while n2 is not allowed to.
	 * 
	 * @param g1
	 * @param g2
	 * @param n1
	 * @param n2
	 * @return
	 */
	public static MatchResult getMatchResult(ModuleGraphCluster left, ModuleGraphCluster right, Node leftNode,
			ExecutionNode rightNode, boolean isIndirect) {
		SoftwareDistributionUnit rightUnit = rightNode.getModule().unit;

		// Cannot normalized tags from unknown modules
		if (rightUnit == SoftwareDistributionUnit.UNKNOWN) {
			return MatchResult.Unknown;
		}

		Node leftCorrespondingToRight = left.getGraphData().HACK_relativeTagLookup(rightNode);
		if (leftCorrespondingToRight == null) {
			// The corresponding node does not exist in graph1
			// 1. n1 == null, non-existing correct match
			// 2. n1 != null, non-existing mismatch
			if (leftNode == null) {
				if (isIndirect) {
					return MatchResult.IndirectNonExistingCorrectMatch;
				} else {
					return MatchResult.PureHeuristicsNonExistingCorrectMatch;
				}
			} else {
				if (isIndirect) {
					return MatchResult.IndirectNonExistingMismatch;
				} else {
					return MatchResult.PureHeuristicsNonExistingMismatch;
				}
			}
		} else {
			// The corresponding node does exist in graph1
			// 1. n1 == null, existing unfound mismatch
			// 2. n1 != null && t1.equals(t2), existing match
			// 3. n1 != null && !t1.equals(t2), existing mismatch
			if (leftNode == null) {
				if (isIndirect) {
					return MatchResult.IndirectExistingUnfoundMismatch;
				} else {
					return MatchResult.PureHeuristicsExistingUnfoundMismatch;
				}
			} else {
				if (leftCorrespondingToRight.getKey().equals(rightNode.getKey())) {
					if (isIndirect) {
						return MatchResult.IndirectExistingCorrectMatch;
					} else {
						return MatchResult.PureHeuristicsExistingCorrectMatch;
					}
				} else {
					if (isIndirect) {
						return MatchResult.IndirectExistingMismatch;
					} else {
						return MatchResult.PureHeuristicsExistingMismatch;
					}
				}
			}
		}
	}

	/**
	 * Input node n1 and n2 are matched nodes and they have indirect outgoing edges. This function analyzes how
	 * difficult it is to match its indirect outgoing nodes according the hash collision of those nodes.
	 * 
	 * @param n1
	 * @param n2
	 */
	public static void outputIndirectNodesInfo(ExecutionNode n1, ExecutionNode n2) {
		Log.log("Start indirect node pair info output: " + n1 + " & " + n2);
		HashMap<Long, Integer> hash2CollisionCnt = new HashMap<Long, Integer>();
		for (int i = 0; i < n1.getOutgoingEdges().size(); i++) {
			long hash = n1.getOutgoingEdges().get(i).getToNode().getHash();
			if (!hash2CollisionCnt.containsKey(hash)) {
				hash2CollisionCnt.put(hash, 0);
			}
			hash2CollisionCnt.put(hash, hash2CollisionCnt.get(hash) + 1);
		}
		for (int i = 0; i < n2.getOutgoingEdges().size(); i++) {
			long hash = n2.getOutgoingEdges().get(i).getToNode().getHash();
			if (!hash2CollisionCnt.containsKey(hash)) {
				hash2CollisionCnt.put(hash, 0);
			}
			hash2CollisionCnt.put(hash, hash2CollisionCnt.get(hash) + 1);
		}
		for (long hash : hash2CollisionCnt.keySet()) {
			int cnt = hash2CollisionCnt.get(hash);
			if (cnt > 2) {
				Log.log(Long.toHexString(hash) + ": " + cnt);
			}
		}
		Log.log("Finish indirect node pair info output.");
	}

	/**
	 * <pre>
	public static void outputUnknownTags(ProcessExecutionGraph graph) {
		long minUnknownTag = Long.MAX_VALUE, maxUnknownTag = Long.MIN_VALUE;
		int unknownTagCnt = 0;
		for (int i = 0; i < graph.getNodes().size(); i++) {
			Node n = graph.getNodes().get(i);
			NormalizedTag t = new NormalizedTag(n);
			if (t.moduleName.equals("Unknown")) {
				unknownTagCnt++;
				if (n.getTag().tag > maxUnknownTag) {
					maxUnknownTag = n.getTag().tag;
				}
				if (n.getTag().tag < minUnknownTag) {
					minUnknownTag = n.getTag().tag;
				}
				System.out.print(n);
				int fromIdx = n.getIncomingEdges().size() == 0 ? -1 : n
						.getIncomingEdges().get(0).getFromNode().getIndex();
				int toIdx = n.getOutgoingEdges().size() == 0 ? -1 : n
						.getOutgoingEdges().get(0).getToNode().getIndex();
				Log.log(" _ " + fromIdx + "_" + toIdx);
			}
		}
		Log.log("Max unknown tag: "
				+ Long.toHexString(maxUnknownTag));
		Log.log("Min unknown tag: "
				+ Long.toHexString(minUnknownTag));
	}

	public static void outputTagComparisonInfo(ProcessExecutionGraph graph1,
			ProcessExecutionGraph graph2) {
		Log.log("New tags comparison for " + graph1 + " & " + graph2);
		Log.log("New tags for graph1: " + graph1);
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n = graph1.getNodes().get(i);
			NormalizedTag t = new NormalizedTag(n);
			if (!graph2.normalizedTag2Node.containsKey(t)
					&& t.moduleName.indexOf("Unknown") == -1) {
				// if (t.moduleName.indexOf("HexEdit") != -1) {
				Log.log(t);
				// }
			}
		}

		Log.log("New tags for graph2: " + graph2);
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node n = graph2.getNodes().get(i);
			NormalizedTag t = new NormalizedTag(n);
			if (!graph1.normalizedTag2Node.containsKey(t)
					&& t.moduleName.indexOf("Unknown") == -1) {
				// if (t.moduleName.indexOf("HexEdit") != -1) {
				Log.log(t);
				// }

			}
		}

		String modName = "comctl32.dll-1db1446a0006000a";
		long relTag = Long.valueOf("2ec55", 16).longValue();
		NormalizedTag tag = new NormalizedTag(modName, relTag);
		Node n = graph2.normalizedTag2Node.get(tag), previous_n = n
				.getIncomingEdges().get(0).getFromNode();
		;
		NormalizedTag previous_tag2 = new NormalizedTag(previous_n);
		while (!graph1.normalizedTag2Node.containsKey(previous_tag2)) {
			n = graph2.normalizedTag2Node.get(tag);
			previous_n = n.getIncomingEdges().get(0).getFromNode();
			previous_tag2 = new NormalizedTag(previous_n);
			Log.log(n);
			if (graph1.normalizedTag2Node.containsKey(previous_tag2)) {
				Log.log(previous_n);
				Log.log(n);
			}
			tag = previous_tag2;
		}
		Log.log(previous_n);
	}
	 */
}
