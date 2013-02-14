import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;

public class ClusteringTest {
//	private class HashSetCache {
//		int size;
//		HashSet<Long>[] hashes;
//		public HashSetCache() {
//			this.size = 20;
//			this.hashes = new HashSet[size];
//			
//		}
//	}
	
	private HashSet<Long>[] hashes;
	private HashMap<Long, Integer> freqTable;
	private HashMap<Integer, HashSet<Long>> cacheHashes;
	private float[][] distMatrix;
	private float[] factors;
	private String[] fileNames;
	private StringBuilder strBuilder;
	

	public ClusteringTest() {

	}

	public ClusteringTest(String[] filenames) {
		this.fileNames = filenames;
		init();
		computeAllDist();
	}

	public void computeAllDist() {
		for (int i = 0; i < factors.length; i++) {
			factors[i] = 0.0f;
			HashSet<Long> set = AnalysisUtil.initSetFromFile(fileNames[i]);
			for (Long l : set) {
				factors[i] += (1.0f / (freqTable.get(l) * freqTable.get(l)));
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
					System.out.println("Time for 1000 distance computation" + (endTime - beginTime) + " miliseconds");
					beginTime = System.currentTimeMillis();
					count = 0;
				}
				count++;
				distMatrix[i][j] = computeDist(i, j);
				distMatrix[j][i] = distMatrix[i][j];
			}
		}
		veryEnding = System.currentTimeMillis();
		System.out.println("Total time:" + (veryEnding - veryBeginning) / 1000 + " seconds"); 
	}
	
	private void merge() {
		
	}

	public void outputDistMatrix() {
		for (int i = 0; i < fileNames.length; i++) {
			System.out.println(fileNames[i]);
		}
		System.out.printf("%8s", "");
		for (int i = 0; i < fileNames.length; i++) {
			System.out.printf("%8s", i + AnalysisUtil.getProgName(fileNames[i]));
		}
		System.out.println();
		float min = 1.5f, max = 1.5f;
		for (int i = 0; i < fileNames.length; i++) {
			System.out.printf("%8s", i + AnalysisUtil.getProgName(fileNames[i]));
			for (int j = 0; j < fileNames.length; j++) {
				System.out.printf("% 8.2f", distMatrix[i][j]);
				if (distMatrix[i][j] < 1.5f && j > i) {
					strBuilder.append(i + AnalysisUtil.getProgName(fileNames[i]) + " " + j + AnalysisUtil.getProgName(fileNames[j]) + " " + distMatrix[i][j] + "\n");
					if (distMatrix[i][j] < min)
						min = distMatrix[i][j];
				}
				if (distMatrix[i][j] > max)
					max = distMatrix[i][j];
			}
			System.out.println();
		}
		System.out.println("Minimum distance: " + min);
		System.out.println("Maximum distance: " + max);
		System.out.print(strBuilder.toString());
	}

	private float computeDist(int i, int j) {
		HashSet<Long> hash1 = AnalysisUtil.initSetFromFile(fileNames[i]),
				hash2 = AnalysisUtil.initSetFromFile(fileNames[j]);
		HashSet<Long> inter = AnalysisUtil.intersection(hash1, hash2);
//		float dist = 0.0f, factor = (float) inter.size()
//				/ (hash1.size() + hash2.size() - inter.size());
		float dist = 0.0f;
		for (Long l : inter) {
			dist += 1.0f / (freqTable.get(l) * freqTable.get(l));
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
			hashes[i] = AnalysisUtil.initSetFromFile(fileNames[i]);
			HashSet<Long> set = AnalysisUtil.initSetFromFile(fileNames[i]);
			for (Long l : set) {
				if (freqTable.keySet().contains(l)) {
					freqTable.put(l, freqTable.get(l) + 1);
				} else {
					freqTable.put(l, 1);
				}
			}
		}
	}

	public static void main(String[] argvs) {
		ArrayList<File> hashFiles = AnalysisUtil.getAllHashFiles(argvs[0]);
		String[] fileNames = new String[hashFiles.size()];
		for (int i = 0; i < fileNames.length; i++) {
			fileNames[i] = hashFiles.get(i).getAbsolutePath();
		}
		ClusteringTest test = new ClusteringTest(fileNames);
		test.outputDistMatrix();
	}
}
