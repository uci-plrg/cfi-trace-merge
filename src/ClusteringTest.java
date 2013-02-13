import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;

public class ClusteringTest {
	private HashSet<Long>[] hashes;
	private HashMap<Long, Integer> freqTable;
	private float[][] distMatrix;
	private String[] fileNames;

	public ClusteringTest() {

	}

	public ClusteringTest(String[] filenames) {
		this.fileNames = filenames;
		init();
		computeAllDist();
	}

	public void computeAllDist() {
		for (int i = 0; i < hashes.length; i++) {
			for (int j = i; j < hashes.length; j++) {
				distMatrix[i][j] = computeDist(i, j);
			}
		}
	}

	public void outputDistMatrix() {
		for (int i = 0; i < fileNames.length; i++) {
			System.out.println(fileNames[i]);
		}
		System.out.printf("%8s", "");
		for (int i = 0; i < fileNames.length; i++) {
			System.out.printf("%8s", AnalysisUtil.getProgName(fileNames[i]));
		}
		System.out.println();
		for (int i = 0; i < hashes.length; i++) {
			System.out.printf("%8s", AnalysisUtil.getProgName(fileNames[i]));
			for (int j = 0; j < hashes.length; j++) {
				System.out.printf("% 8.1f", distMatrix[i][j]);
			}
			System.out.println();
		}
	}

	private float computeDist(int i, int j) {
		HashSet<Long> inter = AnalysisUtil.intersection(hashes[i], hashes[j]);
		float dist = 0.0f, factor = (float) inter.size()
				/ (hashes[i].size() + hashes[j].size() - inter.size());
		for (Long l : inter) {
			dist += 1.0f / freqTable.get(l);
		}
		return factor * dist;
	}

	private void init() {
		distMatrix = new float[fileNames.length][fileNames.length];
		hashes = new HashSet[fileNames.length];
		freqTable = new HashMap<Long, Integer>();

		for (int i = 0; i < fileNames.length; i++) {
			hashes[i] = AnalysisUtil.initSetFromFile(fileNames[i]);
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
		ArrayList<File> hashFiles = AnalysisUtil.getAllHashFiles(argvs[0]);
		String[] fileNames = new String[hashFiles.size()];
		for (int i = 0; i < fileNames.length; i++) {
			fileNames[i] = hashFiles.get(i).getAbsolutePath();
		}
		ClusteringTest test = new ClusteringTest(fileNames);
		test.outputDistMatrix();
	}
}
