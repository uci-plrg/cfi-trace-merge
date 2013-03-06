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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AnalysisUtil {
	public static final ByteOrder byteOrder = ByteOrder.nativeOrder();

	// In format of "tar.bb-graph-hash.2013-03-06.06-44-36.4947-4947.dat"
	public int getPidFromFileName(String fileName) {
		int secondLastDotPos = fileName.lastIndexOf('.', fileName.lastIndexOf('.')),
				lastDashPos = fileName.lastIndexOf('-');
		String pidStr = fileName.substring(secondLastDotPos + 1, lastDashPos);
		return Integer.parseInt(pidStr);
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
			while((str = br.readLine()) != null) {
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

	public static HashSet<Long> intersection(HashSet<Long> s1, HashSet<Long> s2) {
		HashSet<Long> res = new HashSet<Long>(s1);
		res.retainAll(s2);
		return res;
	}

	public static Long reverseForLittleEndian(Long l) {
		if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
			ByteBuffer bbuf = ByteBuffer.allocate(8);
			bbuf.order(ByteOrder.BIG_ENDIAN);
			bbuf.putLong(l);
			bbuf.order(ByteOrder.LITTLE_ENDIAN);
			return bbuf.getLong(0);
		}
		return l;
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
//		try {
//			dirName.substring(0, endIndex);
//		} catch (Exception e) {
//			System.out.println(dirName);
//		}
		return dirName.substring(0, endIndex);
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
	
	public static HashSet<Long> mergeSet(HashSet<Long>...sets) {
		HashSet<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < sets.length; i++) {
			resSet.addAll(sets[i]);
		}
		return resSet;
	}

	
	public static HashSet<Long> mergeSet(String...hashFiles) {
		HashSet<Long> resSet = new HashSet<Long>();
		for (int i = 0; i < hashFiles.length; i++) {
			resSet.addAll(initSetFromFile(hashFiles[i]));
		}
		return resSet;
	}

	public static HashSet<Long> initSetFromFile(File hashFile) {
		return initSetFromFile(hashFile.getAbsolutePath());
	}
	
	public static HashSet<Long> initSetFromFile(String fileName) {
		DataInputStream in = null;
		try {
			in = new DataInputStream(new FileInputStream(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		HashSet<Long> set = new HashSet<Long>();

		try {
			// int bytesLeft = in.available() / 8;
			Long hashCode;
			while (true) {
				hashCode = in.readLong();
				set.add(reverseForLittleEndian(hashCode));
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

			System.out.println("Start outputing hash set to " + outputFileName
					+ " file.");

			for (Long l : hashset) {
				dataOutput.writeLong(reverseForLittleEndian(l));
			}

			System.out.println("Finish outputing hash set to " + outputFileName
					+ " file.");
			outputStream.close();
			dataOutput.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
