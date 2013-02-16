package analysis;

import gnu.getopt.Getopt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;

import utils.AnalysisUtil;

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
	private float[][] distMatrix;
	private float[] scores;
	private String[] paths;
	private String[] progNames;

	public ClusteringAnalysis() {

	}

	public ClusteringAnalysis(String[] paths) {
		this.paths = paths;
		progNames = new String[paths.length];
		for (int i = 0; i < paths.length; i++) {
			progNames[i] = AnalysisUtil.getProgNameFromPath(paths[i]);
		}
		init();
		computeAllDist();
	}

	public void computeAllDist() {
		for (int i = 0; i < scores.length; i++) {
			scores[i] = 0.0f;
			for (Long l : hashes[i]) {
				// factors[i] += (1.0f / (freqTable.get(l) * freqTable.get(l)));
				scores[i] += (1.0f / freqTable.get(l));
				// factors[i] += 1.0f;
			}
		}

		int count = 0;
		long beginTime, endTime, veryBeginning, veryEnding;
		veryBeginning = System.currentTimeMillis();
		beginTime = System.currentTimeMillis();
		for (int i = 0; i < paths.length; i++) {
			for (int j = i; j < paths.length; j++) {
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
		for (int i = 0; i < paths.length; i++) {
			System.out.println(paths[i]);
		}
		System.out.printf("%7s", "");
		for (int i = 0; i < paths.length; i++) {
			System.out.printf("%7s", i + progNames[i]);
		}
		System.out.println();
		float min = 1.5f, max = 1.5f;
		StringBuilder strBuilder = new StringBuilder();
		for (int i = 0; i < paths.length; i++) {
			System.out.printf("%7s", i + progNames[i]);
			for (int j = 0; j < paths.length; j++) {
				if (i <= j)
					System.out.printf("% 7.1f", distMatrix[i][j]);
				else
					System.out.printf("%7s", " ");
				if (distMatrix[i][j] < 1.5f && j > i) {
					String progName1 = progNames[i], progName2 = progNames[j];
					String str = "";
					if (progName1.equals(progName2)) {
						str = String.format("%d%s %d%s :%.1f\n", i, progName1,
								j, progName2, distMatrix[i][j]);
					} else {
						str = String.format("*%d%s %d%s :%.1f\n", i, progName1,
								j, progName2, distMatrix[i][j]);
					}
					strBuilder.append(str);
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
	
	/**
	 * This function needs to analyze how well the distance equation works,
	 * especially in two aspects: 1. the minimum distance of different runs of
	 * the same program; 2. the maximum distance of different runs of different
	 * programs.
	 */
	public void analyze() {
		
	}

	private float computeDist(int i, int j) {
		HashSet<Long> inter = AnalysisUtil.intersection(hashes[i], hashes[j]);
		float interScore = 0.0f;
		for (Long l : inter) {
			interScore += 1.0f / freqTable.get(l);
		}
		return 2 / (interScore / scores[i] + interScore / scores[j]) - 1;
	}

	private void init() {
		distMatrix = new float[paths.length][paths.length];
		scores = new float[paths.length];
		hashes = new HashSet[paths.length];
		freqTable = new HashMap<Long, Integer>();

		for (int i = 0; i < paths.length; i++) {
			hashes[i] = AnalysisUtil.getSetFromPath(paths[i]);
			for (Long l : hashes[i]) {
				if (freqTable.keySet().contains(l)) {
					freqTable.put(l, freqTable.get(l) + 1);
				} else {
					freqTable.put(l, 1);
				}
			}
		}
	}

	
	public static void main(String[] argvs) {

		Getopt g = new Getopt("ClusteringAnalysis", argvs, "o::f:d:");
		int c;
		boolean append = true, error = false;
		String dir4Files = null, dir4Runs = null,
			recordFile = null;
		while ((c = g.getopt()) != -1) {
			switch (c) {
				case 'o':
					append = false;
					break;
				case 'f':
					dir4Files = g.getOptarg();
					if (dir4Files.startsWith("-"))
						error = true;
					break;
				case 'd':
					dir4Runs = g.getOptarg();
					if (dir4Runs.startsWith("-"))
						error = true;
					break;
				case '?':
					error = true;
					System.out.println("parse error for option: -" + (char) g.getOptopt());
					break;
				default:
					break;	
			}
		}
		if (error)
			return;
		int index = g.getOptind();
		if (index < argvs.length)
			recordFile = argvs[index];
		else {
			System.out.println("Usage: ClusteringAnalysis [-o][-f dir][-d dir] file");
			return;
		}
		
		if (!(dir4Files == null && dir4Runs == null)) {
			ArrayList<String> listFiles = new ArrayList<String>();
			if (dir4Files != null) {
				listFiles = AnalysisUtil.getAllHashFiles(dir4Files);
			}
			if (dir4Runs != null) {
				ArrayList<String> list = AnalysisUtil.getAllRunDirs(dir4Runs);
				listFiles.addAll(list);
			}
			AnalysisUtil.saveStringPerline(recordFile, listFiles, append);
		}
		ArrayList<String> strList = AnalysisUtil.getStringPerline(recordFile);
		String[] strArray = strList.toArray(new String[strList.size()]);
		
		ClusteringAnalysis clusterAnalysis = new ClusteringAnalysis(strArray);
		clusterAnalysis.outputDistMatrix();
	}
}
