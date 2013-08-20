package utils;

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

import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

public class AnalysisUtil {
	public static final ByteOrder byteOrder = ByteOrder.nativeOrder();

	// In format of "tar.bb-graph-hash.2013-03-06.06-44-36.4947-4947.dat"
	public static int getPidFromFile(File file) {
		String filename = file.getName();
		int lastDashPos = filename.lastIndexOf('-');
		int lastDotPos = filename.lastIndexOf('.');
		return Integer
				.parseInt(filename.substring(lastDashPos + 1, lastDotPos));
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

	public static void saveStringPerline(String filename,
			ArrayList<String> list, boolean append) {
		try {
			PrintWriter pw = new PrintWriter(new FileOutputStream(filename,
					append));
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

			FileOutputStream outputStream = new FileOutputStream(outputFile,
					false);
			DataOutputStream dataOutput = new DataOutputStream(outputStream);

			System.out.println("Start outputting hash set to "
					+ outputFile.getAbsolutePath() + " file.");

			System.out.println("Finish outputting hash set to "
					+ outputFile.getAbsolutePath() + " file.");
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
	 * @param g1
	 * @param g2
	 */
	/**
	 * <pre>
	public static void filteroutImmeAddr(ProcessExecutionGraph g1,
			ProcessExecutionGraph g2) {
		int modificationCnt = 0;

		PrintWriter pw = null;
		if (DebugUtils.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
			try {
				String fileName = g1.dataSource.getProcessName()
						+ ".imme-addr." + g1.dataSource.getProcessId() + "-"
						+ g2.dataSource.getProcessId() + ".txt";
				String absolutePath = DebugUtils.MODIFIED_HASH_DIR + "/"
						+ fileName;
				File f = new File(DebugUtils.MODIFIED_HASH_DIR);
				f.mkdirs();
				pw = new PrintWriter(absolutePath);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

		for (NormalizedTag t : g1.normalizedTag2Node.keySet()) {
			Node n1 = g1.normalizedTag2Node.get(t), n2 = g2.normalizedTag2Node
					.get(t);
			if (n2 == null) {
				continue;
			} else {
				if (n1.getHash() != n2.getHash()) {
					n1.setHash(n2.getHash());
					modificationCnt++;
					if (DebugUtils
							.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
						pw.print("tag1: 0x" + Long.toHexString(n1.getTag().tag));
						pw.println("\t" + t.moduleName + "\t0x"
								+ Long.toHexString(t.relativeTag));

						pw.print("tag2: 0x" + Long.toHexString(n2.getTag().tag));
						pw.println("\t" + t.moduleName + "\t0x"
								+ Long.toHexString(t.relativeTag));

						pw.println();
					}
				}
			}
		}
		if (DebugUtils.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
			pw.flush();
			pw.close();
		}

		System.out.println("Total number of hash modification: "
				+ modificationCnt);
	}

	public static Node getTrueMatch(ProcessExecutionGraph g1,
			ProcessExecutionGraph g2, Node n2) {
		long relativeTag2 = AnalysisUtil.getRelativeTag(g2, n2.getTag().tag);
		String modUnit2 = g2.getModules().getSoftwareUnit(n2.getTag().tag);
		NormalizedTag t2 = new NormalizedTag(modUnit2, relativeTag2);
		return g1.normalizedTag2Node.get(t2);
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
	 * /
	public static MatchResult getMatchResult(ProcessExecutionGraph g1,
			ProcessExecutionGraph g2, Node n1, Node n2, boolean isIndirect) {
		long relativeTag2 = AnalysisUtil.getRelativeTag(g2, n2.getTag().tag), relativeTag1 = n1 == null ? -1
				: AnalysisUtil.getRelativeTag(g1, n1.getTag().tag);
		SoftwareDistributionUnit modUnit2 = g2.getModules().getSoftwareUnit(
				n2.getTag().tag);
		SoftwareDistributionUnit modUnit1 = n1 == null ? null : g1.getModules()
				.getSoftwareUnit(n1.getTag().tag);
		// Cannot normalized the tag
		if (modUnit2 == SoftwareDistributionUnit.UNKNOWN) {
			return MatchResult.Unknown;
		}
		NormalizedTag t2 = new NormalizedTag(modUnit2, relativeTag2);
		NormalizedTag t1 = n1 == null ? null : new NormalizedTag(modUnit1,
				relativeTag1);

		Node correspondingNode = g1.normalizedTag2Node.get(t2);
		if (correspondingNode == null) {
			// The corresponding node does not exist in graph1
			// 1. n1 == null, non-existing correct match
			// 2. n1 != null, non-existing mismatch
			if (n1 == null) {
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
			if (n1 == null) {
				if (isIndirect) {
					return MatchResult.IndirectExistingUnfoundMismatch;
				} else {
					return MatchResult.PureHeuristicsExistingUnfoundMismatch;
				}
			} else {
				if (t1.equals(t2)) {
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
	 */

	/**
	 * Input node n1 and n2 are matched nodes and they have indirect outgoing edges. This function analyzes how
	 * difficult it is to match its indirect outgoing nodes according the hash collision of those nodes.
	 * 
	 * @param n1
	 * @param n2
	 */
	public static void outputIndirectNodesInfo(ExecutionNode n1, ExecutionNode n2) {
		System.out.println("Start indirect node pair info output: " + n1
				+ " & " + n2);
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
				System.out.println(Long.toHexString(hash) + ": " + cnt);
			}
		}
		System.out.println("Finish indirect node pair info output.");
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
				System.out.println(" _ " + fromIdx + "_" + toIdx);
			}
		}
		System.out.println("Unknown tag count: " + unknownTagCnt);
		System.out.println("Max unknown tag: "
				+ Long.toHexString(maxUnknownTag));
		System.out.println("Min unknown tag: "
				+ Long.toHexString(minUnknownTag));
	}

	public static void outputTagComparisonInfo(ProcessExecutionGraph graph1,
			ProcessExecutionGraph graph2) {
		System.out
				.println("New tags comparison for " + graph1 + " & " + graph2);
		System.out.println("New tags for graph1: " + graph1);
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n = graph1.getNodes().get(i);
			NormalizedTag t = new NormalizedTag(n);
			if (!graph2.normalizedTag2Node.containsKey(t)
					&& t.moduleName.indexOf("Unknown") == -1) {
				// if (t.moduleName.indexOf("HexEdit") != -1) {
				System.out.println(t);
				// }
			}
		}

		System.out.println("New tags for graph2: " + graph2);
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node n = graph2.getNodes().get(i);
			NormalizedTag t = new NormalizedTag(n);
			if (!graph1.normalizedTag2Node.containsKey(t)
					&& t.moduleName.indexOf("Unknown") == -1) {
				// if (t.moduleName.indexOf("HexEdit") != -1) {
				System.out.println(t);
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
			System.out.println(n);
			if (graph1.normalizedTag2Node.containsKey(previous_tag2)) {
				System.out.println("extra info:");
				System.out.println(previous_n);
				System.out.println(n);
			}
			tag = previous_tag2;
		}
		System.out.println(previous_n);
	}
	 */

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
	public static ExecutionNode.Key getNodeKey(long tag) {
		long tagLong = tag << 24 >>> 24;
		int versionNumber = (new Long(tag >>> 56)).intValue();
		return new ExecutionNode.Key(tagLong, versionNumber);
	}
}
