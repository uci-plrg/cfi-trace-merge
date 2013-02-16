package analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;

import utils.AnalysisUtil;
import utils.gnu.getopt.Getopt;

/**
 * There are two steps to use this class:
 * 1. Extract all the execution source paths and store them in a file (one in a line),
 *    the line could be the specific hash file or the directory that contains all the
 *    hash file for that run.
 * 2. Read that file to initialize all the hash codes for each execution, then analyze them.
 *
 */

public class ClusteringAnalysis {
	private HashSet<Long>[] hashes;
	private HashMap<Long, Integer> freqTable;
	//private HashMap<Integer, HashSet<Long>> cacheHashes;
	private float[][] distMatrix;
	private float[] factors;
	private String[] fileNames;
	private File[] runDirs;
	private StringBuilder strBuilder;

	public ClusteringAnalysis() {

	}

	public ClusteringAnalysis(File[] runDirs) {
		this.runDirs = runDirs;
		this.fileNames = new String[runDirs.length];
		for (int i = 0; i < fileNames.length; i++) {
			fileNames[i] = runDirs[i].getAbsolutePath();
		}
		init();
		computeAllDist();
	}

	public ClusteringAnalysis(String[] paths) {
		this.fileNames = paths;
		this.runDirs = new File[paths.length];
		for (int i = 0; i < paths.length; i++) {
			File f = new File(paths[i]);
			runDirs[i] = f;
			if (f.isDirectory() && f.getName().indexOf("run") != -1) {
				fileNames[i] = f.getParentFile().getName();
			} else if(f.getName().indexOf("pair-hash") != -1) {
				fileNames[i] = f.getName();
			}
		}
		init();
		computeAllDist();
	}

	public void computeAllDist() {
		for (int i = 0; i < factors.length; i++) {
			factors[i] = 0.0f;
			// HashSet<Long> set = AnalysisUtil.initSetFromFile(fileNames[i]);
			for (Long l : hashes[i]) {
				// factors[i] += (1.0f / (freqTable.get(l) * freqTable.get(l)));
				factors[i] += (1.0f / freqTable.get(l));
				// factors[i] += 1.0f;
			}
		}

		int count = 0;
		long beginTime, endTime, veryBeginning, veryEnding;
		veryBeginning = System.currentTimeMillis();
		beginTime = System.currentTimeMillis();
		for (int i = 0; i < fileNames.length; i++) {
			for (int j = i; j < fileNames.length; j++) {
				if (count == 1000) {
					endTime = System.currentTimeMillis();
					System.out.println("Time for 1000 distance computation"
							+ (endTime - beginTime) + " miliseconds");
					beginTime = System.currentTimeMillis();
					count = 0;
				}
				count++;
				distMatrix[i][j] = computeDist(i, j);
				distMatrix[j][i] = distMatrix[i][j];
			}
		}
		veryEnding = System.currentTimeMillis();
		System.out.println("Total time:" + (veryEnding - veryBeginning) / 1000
				+ " seconds");
	}

	private void merge() {

	}

	public void outputDistMatrix() {
		for (int i = 0; i < fileNames.length; i++) {
			System.out.println(fileNames[i]);
		}
		System.out.printf("%7s", "");
		for (int i = 0; i < fileNames.length; i++) {
			System.out.printf("%7s", i + AnalysisUtil.getProgName(fileNames[i]));
		}
		System.out.println();
		float min = 1.5f, max = 1.5f;
		for (int i = 0; i < fileNames.length; i++) {
			System.out.printf("%7s", i + AnalysisUtil.getProgName(fileNames[i]));
			for (int j = 0; j < fileNames.length; j++) {
				// if (i <= j)
				System.out.printf("% 7.1f", distMatrix[i][j]);
				// else
				// System.out.printf("%7s", " ");
				if (distMatrix[i][j] < 1.5f && j > i) {
					String progName1 = AnalysisUtil.getProgName(fileNames[i]), progName2 = AnalysisUtil
							.getProgName(fileNames[j]);
					String str = "";
					if (progName1.equals(progName2)) {
						str = String.format("%d%s %d%s :%.1f\n", i, progName1,
								j, progName2, distMatrix[i][j]);
					} else {
						str = String.format("*%d%s %d%s :%.1f\n", i, progName1,
								j, progName2, distMatrix[i][j]);
					}
					strBuilder.append(str);
					// strBuilder.append(i +
					// AnalysisUtil.getProgName(fileNames[i]) + " " + j +
					// AnalysisUtil.getProgName(fileNames[j]) + " " +
					// distMatrix[i][j] + "\n");
					if (distMatrix[i][j] < min)
						min = distMatrix[i][j];
				}
				if (distMatrix[i][j] > max)
					max = distMatrix[i][j];
			}
			System.out.println();
		}
		System.out.println("\n");
		System.out.printf("Minimum distance: %.1f\n", min);
		System.out.printf("Maximum distance: %.1f\n", max);
		System.out.print(strBuilder.toString());
	}

	private float computeDist(int i, int j) {
		HashSet<Long> hash1 = hashes[i], hash2 = hashes[j];
		// HashSet<Long> hash1 = AnalysisUtil.initSetFromFile(fileNames[i]),
		// hash2 = AnalysisUtil.initSetFromFile(fileNames[j]);
		HashSet<Long> inter = AnalysisUtil.intersection(hash1, hash2);
		// float dist = 0.0f, factor = (float) inter.size()
		// / (hash1.size() + hash2.size() - inter.size());
		float dist = 0.0f;
		for (Long l : inter) {
			// dist += 1.0f / (freqTable.get(l) * freqTable.get(l));
			dist += 1.0f / freqTable.get(l);
			// dist += 1.0f;
		}
		return 2 / (dist / factors[i] + dist / factors[j]) - 1;
	}

	private void init() {
		distMatrix = new float[fileNames.length][fileNames.length];
		factors = new float[fileNames.length];
		strBuilder = new StringBuilder();
		hashes = new HashSet[fileNames.length];
		freqTable = new HashMap<Long, Integer>();

		for (int i = 0; i < fileNames.length; i++) {
			HashSet<Long> set;
			hashes[i] = AnalysisUtil.getSetFromRunDir(runDirs[i]);
			//hashes[i] = AnalysisUtil.initSetFromFile(fileNames[i]);
			
			set = hashes[i];
			for (Long l : set) {
				// for (Long l : hashes[i]) {
				if (freqTable.keySet().contains(l)) {
					freqTable.put(l, freqTable.get(l) + 1);
				} else {
					freqTable.put(l, 1);
				}
			}
		}
	}

	
	public static void main(String[] argvs) {

		Getopt g = new Getopt("ClusteringAnalysis", argvs, "of:d:");
		int c;
		boolean append = true;
		String dir4Files = null, dir4Runs = null,
			recordFile = null;
		while ((c = g.getopt()) != -1) {
			switch (c) {
				case 'o':
					append = false;
					break;
				case 'f':
					dir4Files = g.getOptarg();
					break;
				case 'd':
					dir4Runs = g.getOptarg();
					break;
				case '?':
					System.out.println("parse error for option: -" + (char) g.getOptopt());
					break;
				default:
					break;	
			}
		}
		
		System.out.println("append: " + append);
		System.out.println("dir4Files: " + dir4Files);
		System.out.println("dir4Runs: " + dir4Runs);
		System.out.println("recordFile: " + recordFile);
		
		
//		if (append) {
//			
//		}
		// ArrayList<File> hashFiles = AnalysisUtil.getAllHashFiles(argvs[0]);
		// String[] fileNames = new String[hashFiles.size()];
		// for (int i = 0; i < fileNames.length; i++) {
		// fileNames[i] = hashFiles.get(i).getAbsolutePath();
		// }
		// Arrays.sort(fileNames);
	
//		ArrayList<File> runDirLists = AnalysisUtil.getAllRunDirs(argvs[0]);
//		File[] runDirs = runDirLists.toArray(new File[runDirLists.size()]);
//		ClusteringAnalysis test = new ClusteringAnalysis(runDirs);
		//ClusteringTest test = new ClusteringTest(fileNames);
		//test.outputDistMatrix();
	}
}
