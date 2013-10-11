package edu.uci.eecs.crowdsafe.merge.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashSpeculationScoreRecord.MatchResult;

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

	@SafeVarargs
	public static Set<Long> mergeSet(Set<Long>... sets) {
		Set<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < sets.length; i++) {
			resSet.addAll(sets[i]);
		}
		return resSet;
	}

	public static Set<Long> loadHashSet(File hashFile) throws IOException {
		FileInputStream in = new FileInputStream(hashFile);
		HashSet<Long> set = new HashSet<Long>();

		try {
			FileChannel channel = in.getChannel();

			ByteBuffer buffer = ByteBuffer.allocate(0x8);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

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
		} finally {
			in.close();
		}
		return set;
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
	public static MatchResult getMatchResult(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right, Node<?> leftNode,
			ExecutionNode rightNode, boolean isIndirect) {
		SoftwareUnit rightUnit = rightNode.getModule().unit;

		// Cannot normalized tags from unknown modules
		if (rightUnit == SoftwareUnit.DYNAMORIO) {
			return MatchResult.Unknown;
		}

		if (left.getGraphData().HACK_containsEquivalent(rightNode)) {
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
				if (isIndirect) {
					return MatchResult.IndirectExistingCorrectMatch;
				} else {
					return MatchResult.PureHeuristicsExistingCorrectMatch;
				}
			}
		}
	}
}
