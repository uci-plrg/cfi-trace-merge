package utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import analysis.exception.graph.OverlapModuleException;
import analysis.graph.debug.DebugUtils;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.ModuleDescriptor;
import analysis.graph.representation.Node;
import analysis.graph.representation.NormalizedTag;
import analysis.graph.representation.SpeculativeScoreRecord.MatchResult;

public class AnalysisUtil {
	public static final ByteOrder byteOrder = ByteOrder.nativeOrder();

	// In format of "tar.bb-graph-hash.2013-03-06.06-44-36.4947-4947.dat"
	public static int getPidFromFileName(String fileName) {
		int secondLastDotPos = 0, lastDashPos = fileName.lastIndexOf('-');
		// int count = 0;
		// for (; count < 4 && secondLastDotPos != -1; count++) {
		// secondLastDotPos = fileName.indexOf('.', secondLastDotPos + 1);
		// }
		secondLastDotPos = fileName.length();
		secondLastDotPos = fileName.lastIndexOf('.', secondLastDotPos);
		secondLastDotPos = fileName.lastIndexOf('.', secondLastDotPos - 1);
		String pidStr = null;
		try {
			pidStr = fileName.substring(secondLastDotPos + 1, lastDashPos);
		} catch (StringIndexOutOfBoundsException e) {
			return 0;
		}
		int pid;
		try {
			pid = Integer.parseInt(pidStr);
		} catch (NumberFormatException e) {
			pid = 0;
		}
		return pid;
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

	public static HashSet<Long> getSetFromRunDir(String runDir) {
		ArrayList<String> fileList = getAllHashFiles(runDir);
		String[] strArray = fileList.toArray(new String[fileList.size()]);
		return mergeSet(strArray);
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

	public static String getProgNameFromPath(String path) {
		File f = new File(path);
		String progName;
		if (f.isDirectory()) {
			if (f.getName().indexOf("run") != -1) {
				progName = f.getParentFile().getName();
				return progName;
			} else {
				return null;
			}
		} else {
			if (f.getName().indexOf("pair-hash") != -1) {
				progName = f.getName();
				return getProgName(progName);
			} else {
				return null;
			}
		}
	}

	public static HashSet<Long> minus(HashSet<Long> s1, HashSet<Long> s2) {
		HashSet<Long> res = new HashSet<Long>(s1);
		for (Long elem : s2)
			if (res.contains(elem))
				res.remove(elem);
		return res;
	}

	public static HashSet<Long> union(HashSet<Long> s1, HashSet<Long> s2) {
		HashSet<Long> res = new HashSet<Long>(s1);
		res.addAll(s2);
		return res;
	}

	public static HashSet<Long> intersection(HashSet<Long> s1, HashSet<Long> s2) {
		HashSet<Long> res = new HashSet<Long>(s1);
		res.retainAll(s2);
		return res;
	}

	public static String getRunStr(String path) {
		if (path == null) {
			return null;
		}
		int runIdx = path.indexOf("run");
		int endIdx = path.indexOf('/', runIdx);
		endIdx = endIdx == -1 ? path.length() : endIdx;
		return path.substring(runIdx, endIdx);
	}

	public static String getRunPath(String path) {
		int runIdx = path.indexOf("run");
		int endIdx = path.indexOf('/', runIdx);
		endIdx = endIdx == -1 ? path.length() : endIdx;
		return path.substring(0, endIdx);
	}

	public static String getBaseNameFromPath(String path, String separator) {
		int lastIndex = path.lastIndexOf(separator);
		if (lastIndex == -1) {
			return null;
		} else {
			return path.substring(lastIndex + 1);
		}
	}

	public static String getBaseName(String dirName, String separator) {
		File f = new File(dirName);
		dirName = f.getName();
		if (dirName.startsWith(separator))
			return null;
		if (dirName.indexOf(separator) == -1)
			return null;
		int endIndex = dirName.indexOf(separator);
		return dirName.substring(0, endIndex);
	}

	public static String getProgName(String dirName) {
		File f = new File(dirName);
		dirName = f.getName();
		if (dirName.startsWith("."))
			return null;
		if (dirName.indexOf('.') == -1)
			return null;
		int endIndex = dirName.indexOf('-');
		if (endIndex > dirName.indexOf('_') && dirName.indexOf('_') != -1) {
			endIndex = dirName.indexOf('_');
		}
		if (endIndex > dirName.indexOf('.') && dirName.indexOf('.') != -1) {
			endIndex = dirName.indexOf('.');
		}
		// try {
		// dirName.substring(0, endIndex);
		// } catch (Exception e) {
		// System.out.println(dirName);
		// }
		return dirName.substring(0, endIndex);
	}

	public static ArrayList<Long> getAllHashInstanceFromPath(String path) {
		File f = new File(path);
		if (!f.exists())
			return null;
		if (f.isDirectory()) {
			return null;
		} else {
			return initAllHashInstanceFromFile(path);
		}
	}

	public static HashSet<Long> getSetFromPath(String path) {
		File f = new File(path);
		if (!f.exists())
			return null;
		if (f.isDirectory()) {
			return getSetFromRunDir(path);
		} else {
			return initSetFromFile(path);
		}
	}

	public static HashSet<Long> mergeSet(HashSet<Long>... sets) {
		HashSet<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < sets.length; i++) {
			resSet.addAll(sets[i]);
		}
		return resSet;
	}

	public static HashSet<Long> mergeSet(String... hashFiles) {
		HashSet<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < hashFiles.length; i++) {
			resSet.addAll(initSetFromFile(hashFiles[i]));
		}
		return resSet;
	}

	public static HashSet<Long> initSetFromFile(File hashFile) {
		return initSetFromFile(hashFile.getAbsolutePath());
	}

	public static ArrayList<Long> initAllHashInstanceFromFile(String fileName) {
		FileInputStream in = null;
		FileChannel channel = null;
		try {
			in = new FileInputStream(fileName);
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

	public static HashSet<Long> initSetFromFile(String fileName) {
		FileInputStream in = null;
		FileChannel channel = null;
		try {
			in = new FileInputStream(fileName);
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

	public static void writeSetToFile(String outputFileName,
			HashSet<Long> hashset) {
		try {
			File f = new File(outputFileName);
			if (!f.exists()) {
				f.createNewFile();
			}

			FileOutputStream outputStream = new FileOutputStream(f, false);
			DataOutputStream dataOutput = new DataOutputStream(outputStream);

			System.out.println("Start outputting hash set to " + outputFileName
					+ " file.");

			for (Long l : hashset) {
				// dataOutput.writeLong(reverseForLittleEndian(l)); // FIXME
			}

			System.out.println("Finish outputting hash set to "
					+ outputFileName + " file.");
			outputStream.close();
			dataOutput.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Assume the module file is organized in the follwoing way: Module
	 * USERENV.dll: 0x722a0000 - 0x722b7000
	 * 
	 * @param fileName
	 * @return
	 * @throws OverlapModuleException
	 */
	public static ArrayList<ModuleDescriptor> getModules(String fileName)
			throws OverlapModuleException {
		ArrayList<ModuleDescriptor> res = new ArrayList<ModuleDescriptor>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			try {
				String line;
				while ((line = br.readLine()) != null) {
					int beginIdx, endIdx;
					String name;
					long beginAddr, endAddr;

					beginIdx = line.indexOf(" ", 0);
					endIdx = line.indexOf(":", 0);
					name = line.substring(beginIdx + 1, endIdx);

					beginIdx = line.indexOf("x", endIdx);
					endIdx = line.indexOf(" ", beginIdx);
					beginAddr = Long.parseLong(
							line.substring(beginIdx + 1, endIdx), 16);

					beginIdx = line.indexOf("x", endIdx);
					endAddr = Long.parseLong(line.substring(beginIdx + 1), 16);

					ModuleDescriptor mod = new ModuleDescriptor(name,
							beginAddr, endAddr);
					res.add(mod);
				}
				// Check if there is any overlap between different modules
				for (int i = 0; i < res.size(); i++) {
					for (int j = i + 1; j < res.size(); j++) {
						ModuleDescriptor mod1 = res.get(i), mod2 = res.get(j);
						if ((mod1.beginAddr < mod2.beginAddr && mod1.endAddr > mod2.beginAddr)
								|| (mod1.beginAddr < mod2.endAddr && mod1.endAddr > mod2.endAddr)) {
							String msg = "Module overlap happens!\n" + mod1
									+ " & " + mod2;
							// throw new OverlapModuleException(msg);
						}
					}
				}

				Collections.sort(res);
				return res;

			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Take advantage of the relative tag to filter out the immediate address
	 * 
	 * @param g1
	 * @param g2
	 */
	public static void filteroutImmeAddr(ExecutionGraph g1, ExecutionGraph g2) {
		int modificationCnt = 0;

		PrintWriter pw = null;
		if (DebugUtils.debug_decision(DebugUtils.DUMP_MODIFIED_HASH)) {
			try {
				String fileName = g1.getProgName() + ".imme-addr."
						+ g1.getPid() + "-" + g2.getPid() + ".txt";
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
						pw.print("tag1: 0x" + Long.toHexString(n1.getTag()));
						pw.println("\t" + t.moduleName + "\t0x"
								+ Long.toHexString(t.relativeTag));

						pw.print("tag2: 0x" + Long.toHexString(n2.getTag()));
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

	public static Node getTrueMatch(ExecutionGraph g1, ExecutionGraph g2,
			Node n2) {
		long relativeTag2 = AnalysisUtil.getRelativeTag(g2, n2.getTag());
		String modName2 = AnalysisUtil.getModuleName(g2, n2.getTag());
		NormalizedTag t2 = new NormalizedTag(modName2, relativeTag2);
		return g1.normalizedTag2Node.get(t2);
	}

	/**
	 * This function is used to cheat when merging two executions from the same
	 * program. It will use the relative tag to verify if this is a correct
	 * match. n1 is allowed to be null while n2 is not allowed to.
	 * 
	 * @param g1
	 * @param g2
	 * @param n1
	 * @param n2
	 * @return
	 */
	public static MatchResult getMatchResult(ExecutionGraph g1,
			ExecutionGraph g2, Node n1, Node n2, boolean isIndirect) {
		long relativeTag2 = AnalysisUtil.getRelativeTag(g2, n2.getTag()), relativeTag1 = n1 == null ? -1
				: AnalysisUtil.getRelativeTag(g1, n1.getTag());
		String modName2 = AnalysisUtil.getModuleName(g2, n2.getTag()), modName1 = n1 == null ? null
				: AnalysisUtil.getModuleName(g1, n1.getTag());
		// Cannot normalized the tag
		if (modName2.equals("Unknown")) {
			return MatchResult.Unknown;
		}
		NormalizedTag t2 = new NormalizedTag(modName2, relativeTag2), t1 = n1 == null ? null
				: new NormalizedTag(modName1, relativeTag1);

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

	public static long getRelativeTag(ExecutionGraph graph, long tag) {
		ArrayList<ModuleDescriptor> modules = graph.getModules();
		for (int i = 0; i < modules.size(); i++) {
			ModuleDescriptor mod = modules.get(i);
			if (mod.compareTo(tag) == 0) {
				return tag - mod.beginAddr;
			}
		}
		return tag;
	}

	public static String getModuleName(ExecutionGraph graph, long tag) {
		ArrayList<ModuleDescriptor> modules = graph.getModules();
		for (int i = 0; i < modules.size(); i++) {
			ModuleDescriptor mod = modules.get(i);
			if (mod.compareTo(tag) == 0) {
				return mod.name;
			}
		}
		return "Unknown";
	}
}
