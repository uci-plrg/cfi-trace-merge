package edu.uci.eecs.crowdsafe.merge.graph;

import edu.uci.eecs.crowdsafe.common.log.Log;
import gnu.getopt.Getopt;

//import analysis.graph.representation.SpeculativeScoreList;

public class GraphAnalyzer {

	// The number of threads on a single machine, it's configurable
	private static int threadGroupSize = 1;

	// Counter of pair comparisons it has done
	private static int pairCnt;

	// The current thread counter
	private static int threadCnt;

	public static void main(String[] argvs) {
		Getopt g = new Getopt("GraphAnalyzer", argvs, "msd:t:");
		int c;
		// By default we will have numThreads number of threads
		boolean error = false, sameProg = false, merge = false;
		String runDirs = null;
		while ((c = g.getopt()) != -1) {
			switch (c) {
				case 'm':
					merge = true;
					break;
				case 's':
					sameProg = true;
					break;
				case 'd':
					runDirs = g.getOptarg();
					break;
				case 't':
					threadGroupSize = Integer.parseInt(g.getOptarg());
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

		if (runDirs == null) {
			error = true;
		}

		if (error) {
			Log.log("Parameter error, correct it first!");
			return;
		}

		/**
		 * <pre>
		if (merge) {
			ExecutionGraph bigGraph = mergeOneGraph(runDirs);
		} else {
			pairComparison(runDirs, sameProg);
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				// SpeculativeScoreList.showGlobalStats();
			}
		}
		 */
	}

	/**
	 * <pre> saving this for refactor
	public static void pairComparison(String dir, boolean runSameProgram) {
		ArrayList<String> runDirs = AnalysisUtil.getAllRunDirs(dir);

		GraphMergerThread[] mergers = new GraphMergerThread[threadGroupSize];
		Thread[] threads = new Thread[threadGroupSize];
		threadCnt = 0;
		pairCnt = 0;

		for (int i = 0; i < runDirs.size(); i++) {
			for (int j = i + 1; j < runDirs.size(); j++) {
				String dirName1 = runDirs.get(i), dirName2 = runDirs.get(j);
				if (dirName1.indexOf("run") == -1
						|| dirName2.indexOf("run") == -1) {
					continue;
				}

				// Run the algorithm on different programs or the same programs
				if (AnalysisUtil.getProgNameFromPath(dirName1).equals(
						AnalysisUtil.getProgNameFromPath(dirName2))) {
					if (!runSameProgram) {
						continue;
					}
				} else {
					if (runSameProgram) {
						continue;
					}
				}
				ArrayList<ExecutionGraph> graphs1 = ExecutionGraph
						.buildGraphsFromRunDir(dirName1), graphs2 = ExecutionGraph
						.buildGraphsFromRunDir(dirName2);
				for (int k = 0; k < graphs1.size(); k++) {
					for (int l = 0; l < graphs2.size(); l++) {
						ExecutionGraph g1 = graphs1.get(k), g2 = graphs2.get(l);

						// Log.log("Current thread: " + threadCnt);
						mergers[threadCnt] = new GraphMergerThread(g1, g2);
						threads[threadCnt] = new Thread(mergers[threadCnt]);
						
						mergers[threadCnt].run();
						threadCnt++;
						pairCnt++;

						if (threadCnt == threadGroupSize) {
							threadCnt = 0;
							Log.log(pairCnt
									+ " pairs have been launched!");
							for (int m = 0; m < threadGroupSize; m++) {
								try {
									threads[m].join();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
		}
	}
	 */
}
