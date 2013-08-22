package edu.uci.eecs.crowdsafe.analysis.merge.set;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.util.AnalysisUtil;
import edu.uci.eecs.crowdsafe.analysis.util.DistancePair;
import edu.uci.eecs.crowdsafe.analysis.util.Heap;
import edu.uci.eecs.crowdsafe.util.log.Log;
import gnu.getopt.Getopt;

/**
 * 
 * There are two steps to use this class: 1. Extract all the execution source paths and store them in a file (one in a
 * line), the line could be the specific hash file or the directory that contains all the hash file for that run. 2.
 * Read that file to initialize all the hash codes for each execution, then analyze them.
 * 
 * 
 * 
 * When calculating the distance matrix, we statically distribute the pairwise calculation workload to each thread (by
 * default the number of threads is Tnum = 16).
 */

public class ClusteringAnalysisSet {
	private Set<Long>[] hashes;
	private Map<Long, Integer> freqTable;
	private float[][] distMatrix;
	private float[] scores;
	private File[] paths;
	private String[] progNames;

	private HashMap<String, Integer> progName2Index;
	private HashMap<Integer, String> index2ProgName;

	private int numThreads = 4;
	public DistCalculationThread[] distThreads;

	// timing
	long veryBeginning, veryEnding;

	private class DistCalculationThread implements Runnable {
		private int fromIndex;
		private int toIndex;
		public Thread thread;

		private DistCalculationThread() {

		}

		public DistCalculationThread(int fromIndex, int toIndex) {
			this.fromIndex = fromIndex;
			this.toIndex = toIndex;
			thread = new Thread(this);
		}

		public void run() {
			int count = 0;
			long beginTime, endTime;
			beginTime = System.currentTimeMillis();
			int N = distMatrix[0].length;

			for (int k = fromIndex; k <= toIndex; k++) {
				if (count == 1000) {
					endTime = System.currentTimeMillis();
					Log.log("Time for 1000 distance computation of "
							+ thread.getName() + " is: "
							+ (endTime - beginTime) + " miliseconds");
					beginTime = System.currentTimeMillis();
					count = 0;
				}
				count++;

				int i = (int) ((2 * N + 1 - Math.sqrt((2 * N + 1) * (2 * N + 1)
						- 8 * k)) / 2), j = k - i * (2 * N - i + 1) / 2 + i;
				float f = computeDist(i, j);
				distMatrix[i][j] = f;
			}

		}

	}

	public ClusteringAnalysisSet() {

	}

	public ClusteringAnalysisSet(File[] paths, int numThreads) {
		this.paths = paths;
		this.numThreads = numThreads;
		progNames = new String[paths.length];

		progName2Index = new HashMap<String, Integer>();
		index2ProgName = new HashMap<Integer, String>();
		int index = 0;
		for (int i = 0; i < paths.length; i++) {
			progNames[i] = "<program-name>"; // AnalysisUtil.getProgNameFromPath(paths[i]);
			if (!progName2Index.containsKey(progNames[i])) {
				progName2Index.put(progNames[i], index);
				index2ProgName.put(index++, progNames[i]);
			}
		}
		init();

		int dimension = distMatrix[0].length, interval = dimension
				* (dimension + 1) / 2 / numThreads, beginIndex = 0, endIndex = dimension
				* (dimension + 1) / 2 - 1;

		computeScoreOfSets();

		distThreads = new DistCalculationThread[numThreads];
		for (int i = 0; i < distThreads.length - 1; i++) {
			distThreads[i] = new DistCalculationThread(beginIndex, beginIndex
					+ interval - 1);
			beginIndex += interval;
		}
		distThreads[distThreads.length - 1] = new DistCalculationThread(
				beginIndex, endIndex);
		// computeAllDist();
	}

	public void parallelComputeAllDist() {
		// record the timing when the threads start
		veryBeginning = System.nanoTime();
		for (int i = 0; i < distThreads.length; i++) {
			distThreads[i].thread.setName("Thread" + i);
			distThreads[i].thread.start();
		}
		for (int i = 0; i < distThreads.length; i++) {
			try {
				distThreads[i].thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void timeElapsed() {
		veryEnding = System.nanoTime();
		Log.log("Total time:" + (veryEnding - veryBeginning) / 1000000000.0f
				+ " seconds");
	}

	public static float scoreOfSet(Set<Long> set, Map<Long, Integer> freqTable) {
		float score = 0.0f;
		for (Long l : set) {
			score += (1.0f / freqTable.get(l));
		}
		return score;
		// return set.size();
	}

	private void computeScoreOfSets() {
		for (int i = 0; i < scores.length; i++) {
			scores[i] = scoreOfSet(hashes[i], freqTable);
		}
	}

	public void computeAllDist() {
		computeScoreOfSets();

		int count = 0;
		long beginTime, endTime, veryBeginning, veryEnding;
		veryBeginning = System.currentTimeMillis();
		beginTime = System.currentTimeMillis();
		for (int i = 0; i < paths.length; i++) {
			for (int j = i; j < paths.length; j++) {
				if (count == 1000) {
					endTime = System.currentTimeMillis();
					Log.log("Time for 1000 distance computation"
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
		Log.log("Total time:" + (veryEnding - veryBeginning) / 1000
				+ " seconds");
	}

	public void outputDistMatrix() {
		// for (int i = 0; i < paths.length; i++) {
		// Log.log(paths[i]);
		// }
		// System.out.printf("%7s", "");
		// for (int i = 0; i < paths.length; i++) {
		// System.out.printf("%7s", i + progNames[i]);
		// }
		// Log.log();
		float min = 1.5f, max = 1.5f;
		StringBuilder strBuilder = new StringBuilder();
		for (int i = 0; i < paths.length; i++) {
			System.out.printf("%7s", i + progNames[i]);
			for (int j = 0; j < paths.length; j++) {
				if (i < j)
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
			Log.log();
		}
		Log.log("\n");
		System.out.printf("Minimum distance: %.1f\n", min);
		System.out.printf("Maximum distance: %.1f\n", max);
		// System.out.print(strBuilder.toString());
	}

	/**
	 * This function needs to analyze how well the distance equation works, especially in two aspects: 1. the minimum
	 * distance of different runs of the same program; 2. the maximum distance of different runs of different programs.
	 */
	public void analyze() {
		int numProgs = progName2Index.size();
		float[] maxDistSameProg = new float[numProgs];
		float[][] minDistDiffProg = new float[numProgs][numProgs];
		// initialize the analysis statistics
		for (int i = 0; i < numProgs; i++) {
			maxDistSameProg[i] = 0.0f;
			for (int j = 0; j < numProgs; j++) {
				minDistDiffProg[i][j] = 100000000.0f;
			}
		}

		float maxSameProg = 0.0f, minDiffProg = 100000.0f;
		int maxIndex = 0, minIndexI = 0, minIndexJ = 0;
		int maxInstanceI = 0, maxInstanceJ = 0, minInstanceI = 0, minInstanceJ = 0;
		float[] maxDistSameProgs = new float[numProgs];
		float[] sumDistSameProgs = new float[numProgs];
		int[] numSameProgs = new int[numProgs];
		int[] maxSameProgInstanceIs = new int[numProgs];
		int[] maxSameProgInstanceJs = new int[numProgs];
		// store the closest N distances (N = 100) between different programs
		int numMinDistDiffProgs = 100;
		Heap<DistancePair> minDistDiffProgs = new Heap<DistancePair>(
				new Comparator<DistancePair>() {
					public int compare(DistancePair a, DistancePair b) {
						return a.compareTo(b);
					}
				});

		for (int i = 0; i < distMatrix[0].length; i++) {
			for (int j = i + 1; j < distMatrix[0].length; j++) {
				int indexI = progName2Index.get(progNames[i]), indexJ = progName2Index
						.get(progNames[j]);
				// same program
				if (indexI == indexJ) {
					sumDistSameProgs[indexI] += distMatrix[i][j];
					numSameProgs[indexI]++;
					if (distMatrix[i][j] > maxDistSameProgs[indexI]) {
						maxDistSameProgs[indexI] = distMatrix[i][j];
						maxSameProgInstanceIs[indexI] = i;
						maxSameProgInstanceJs[indexI] = j;
					}
					if (distMatrix[i][j] > maxDistSameProg[indexI]) {
						maxDistSameProg[indexI] = distMatrix[i][j];
						if (maxSameProg < maxDistSameProg[indexI]) {
							maxSameProg = maxDistSameProg[indexI];
							maxIndex = indexI;
							maxInstanceI = i;
							maxInstanceJ = j;
						}
					}
				} else {
					if (minDistDiffProgs.size() < numMinDistDiffProgs) {
						DistancePair distPair = new DistancePair(
								index2ProgName.get(indexI),
								index2ProgName.get(indexJ), paths[i], paths[j],
								distMatrix[i][j]);
						minDistDiffProgs.insertElem(distPair);
					} else if (minDistDiffProgs.getMaxElem().dist > distMatrix[i][j]) {
						DistancePair distPair = new DistancePair(
								index2ProgName.get(indexI),
								index2ProgName.get(indexJ), paths[i], paths[j],
								distMatrix[i][j]);
						minDistDiffProgs.swapMaxElemWith(distPair);
					}
					if (distMatrix[i][j] < minDistDiffProg[indexI][indexJ]) {
						minDistDiffProg[indexI][indexJ] = distMatrix[i][j];
						if (minDiffProg > minDistDiffProg[indexI][indexJ]) {
							minDiffProg = minDistDiffProg[indexI][indexJ];
							minIndexI = indexI;
							minIndexJ = indexJ;
							minInstanceI = i;
							minInstanceJ = j;
						}
					}
				}
			}
		}

		// output the result
		Log.log("Max distance of the same program("
				+ index2ProgName.get(maxIndex) + "): " + maxSameProg);
		Log.log(paths[maxInstanceJ]);

		Log.log("Min distance of different programs("
				+ index2ProgName.get(minIndexI) + " & "
				+ index2ProgName.get(minIndexJ) + "): " + minDiffProg);
		Log.log(paths[minInstanceJ]);

		for (int i = 0; i < numProgs; i++) {
			Log.log("Average distance of program(" + index2ProgName.get(i)
					+ "): " + sumDistSameProgs[i] / numSameProgs[i]);
			Log.log("Max distance of program(" + index2ProgName.get(i) + "): "
					+ maxDistSameProgs[i]);
			Log.log(paths[maxSameProgInstanceJs[i]]);
		}

		// output the N closest pairs of different programs
		Log.log("The closest pairs of different programs: ");
		while (minDistDiffProgs.size() > 0) {
			DistancePair pair = minDistDiffProgs.removeMaxElem();
			Log.log(pair.progName1 + " & " + pair.progName2 + " dist: "
					+ pair.dist);
			Log.log(pair.path1 + "\n" + pair.path2 + "\n");
		}
	}

	private float computeDist(int i, int j) {
		Set<Long> inter = AnalysisUtil.intersection(hashes[i], hashes[j]);
		float interScore = scoreOfSet(inter, freqTable);
		return 2 / (interScore / scores[i] + interScore / scores[j]) - 1;
	}

	private void init() {
		distMatrix = new float[paths.length][paths.length];
		scores = new float[paths.length];
		hashes = new HashSet[paths.length];
		freqTable = new HashMap<Long, Integer>();

		for (int i = 0; i < paths.length; i++) {
			// hashes[i] = AnalysisUtil.getSetFromPath(paths[i]); TODO: refactor file handling
			for (Long l : hashes[i]) {
				if (freqTable.keySet().contains(l)) {
					freqTable.put(l, freqTable.get(l) + 1);
				} else {
					freqTable.put(l, 1);
				}
			}
		}
	}

	public void storeFreqTableInFile(String fileName) {
		try {
			FileOutputStream fileOut = new FileOutputStream(fileName);
			ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
			objectOut.writeObject(freqTable);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static HashMap<Long, Integer> loadFreqTableFromFile(String fileName) {
		try {
			FileInputStream fileIn = new FileInputStream(fileName);
			ObjectInputStream objectIn = new ObjectInputStream(fileIn);
			return (HashMap<Long, Integer>) objectIn.readObject();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void main(String[] argvs) {

		Getopt g = new Getopt("ClusteringAnalysis", argvs, "xonf:d:t:m:");
		int c;
		int numThreads = 4;
		boolean append = true, isAnalyzing = true, error = false;
		String dir4Files = null, dir4Runs = null, recordFile = null, freqFile = null;
		String dir4Hashset1 = null, dir4Hashset2 = null;
		boolean isDistance = false;
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
				case 't':
					numThreads = Integer.parseInt(g.getOptarg());
					break;
				case 'n':
					isAnalyzing = false;
					break;
				case 'm':
					freqFile = g.getOptarg();
					break;
				case 'x':
					// this option is used to compute the distance of two sets
					// it should work with -m option
					isDistance = true;
					break;
				case '?':
					error = true;
					Log.log("parse error for option: -" + (char) g.getOptopt());
					break;
				default:
					error = true;
					break;
			}
		}
		if (error)
			return;
		int index = g.getOptind();
		HashMap<Long, Integer> freqTable = null;
		if (isDistance) {
			freqTable = loadFreqTableFromFile(freqFile);
			if (index < argvs.length)
				dir4Hashset1 = argvs[index++];
			if (index < argvs.length)
				dir4Hashset2 = argvs[index++];
		} else if (index < argvs.length) {
			recordFile = argvs[index];
		} else {
			Log.log("Usage: ClusteringAnalysis [-o][-f dir][-d dir] file");
			return;
		}

		/**
		 * <pre> TODO: refactor file handling
		// output the distance info and then exit
		if (isDistance) {
			Set<Long> set1 = AnalysisUtil.getSetFromRunDir(dir4Hashset1), set2 = AnalysisUtil
					.getSetFromRunDir(dir4Hashset2), inter = AnalysisUtil
					.intersection(set1, set2);
			float score1 = scoreOfSet(set1, freqTable), score2 = scoreOfSet(
					set2, freqTable), scoreInter = scoreOfSet(inter, freqTable), dist = 2 / (scoreInter
					/ score1 + scoreInter / score2) - 1;
			Log.log("Score of " + dir4Hashset2 + " is: " + score2);
			Log.log("Distance of the two sets is: " + dist);
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

		if (isAnalyzing) {
			ClusteringAnalysisSet clusterAnalysis = new ClusteringAnalysisSet(
					strArray, numThreads);
			if (freqFile != null) {
				clusterAnalysis.storeFreqTableInFile(freqFile);
			}
			clusterAnalysis.parallelComputeAllDist();
			// clusterAnalysis.outputDistMatrix();
			clusterAnalysis.analyze();
			clusterAnalysis.timeElapsed();
		}
		 */
	}
}
