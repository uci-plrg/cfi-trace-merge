package analysis.graph;

import gnu.getopt.Getopt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import analysis.graph.representation.ExecutionGraph;

public class GraphAnalyzer {

	// The number of threads on a single machine, it's configurable
	private static int threadGroupSize = 32;

	public static void main(String[] argvs) {
		// Getopt g = new Getopt("GraphAnalyzer", argvs, "xonf:d:t:m:");
		// int c;
		// // By default we will have numThreads number of threads
		// boolean append = true,
		// isAnalyzing = true,
		// error = false;
		// String dir4Files = null, dir4Runs = null,
		// recordFile = null, freqFile = null;
		// String dir4Hashset1 = null, dir4Hashset2 = null;
		// boolean isDistance = false;
		// while ((c = g.getopt()) != -1) {
		// switch (c) {
		// case 'o':
		// append = false;
		// break;
		// case 'f':
		// dir4Files = g.getOptarg();
		// if (dir4Files.startsWith("-"))
		// error = true;
		// break;
		// case 'd':
		// dir4Runs = g.getOptarg();
		// if (dir4Runs.startsWith("-"))
		// error = true;
		// break;
		// case 't':
		// numThreads = Integer.parseInt(g.getOptarg());
		// break;
		// case 'n':
		// isAnalyzing = false;
		// break;
		// case 'm':
		// freqFile = g.getOptarg();
		// break;
		// case 'x':
		// // this option is used to compute the distance of two sets
		// // it should work with -m option
		// isDistance = true;
		// break;
		// case '?':
		// error = true;
		// System.out.println("parse error for option: -" + (char)
		// g.getOptopt());
		// break;
		// default:
		// error = true;
		// break;
		// }
		// }

		pairComparison(argvs[0]);
	}

	public static void pairComparison(String dir) {
		File file = new File(dir);
		File[] runDirs = file.listFiles();
		ExecutionGraph[] graphs = new ExecutionGraph[runDirs.length];

		GraphMerger[] mergers = new GraphMerger[threadGroupSize];
		int threadCnt = 0;

		for (int i = 0; i < runDirs.length; i++) {
			for (int j = i + 1; j < runDirs.length; j++) {
				if (runDirs[i].getName().indexOf("run") == -1
						|| runDirs[j].getName().indexOf("run") == -1) {
					continue;
				}
				if (graphs[i] == null) {
					graphs[i] = ExecutionGraph.buildGraphsFromRunDir(
							runDirs[i].getAbsolutePath()).get(0);
					GraphMergingInfo.dumpGraph(graphs[i],
							"graph-files/" + graphs[i].getProgName()
									+ graphs[i].getPid() + ".dot");
				}
				if (graphs[j] == null) {
					graphs[j] = ExecutionGraph.buildGraphsFromRunDir(
							runDirs[j].getAbsolutePath()).get(0);
					GraphMergingInfo.dumpGraph(graphs[j],
							"graph-files/" + graphs[j].getProgName()
									+ graphs[j].getPid() + ".dot");
				}

				System.out.println("Current thread: " + threadCnt);
				mergers[threadCnt] = new GraphMerger(graphs[i], graphs[j]);
				mergers[threadCnt].start();
				threadCnt++;

				if (threadCnt == threadGroupSize) {
					threadCnt = 0;
					for (int k = 0; k < threadGroupSize; k++) {
						try {
							mergers[k].join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}

			}
		}
	}
}
