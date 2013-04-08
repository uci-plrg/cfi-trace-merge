package analysis.graph;

import java.io.File;

import analysis.graph.representation.ExecutionGraph;

public class GraphAnalyzer {

	public static void pairComparison(String dir) {
		File file = new File(dir);
		File[] runDirs = file.listFiles();
		ExecutionGraph[] graphs = new ExecutionGraph[runDirs.length];

		int countFailed = 0, countMerged = 0;
		// ExecutionGraph bigGraph = buildGraphsFromRunDir(
		// runDirs[0].getAbsolutePath()).get(0);

		for (int i = 0; i < runDirs.length; i++) {
			for (int j = i + 1; j < runDirs.length; j++) {
				if (runDirs[i].getName().indexOf("run") == -1
						|| runDirs[j].getName().indexOf("run") == -1) {
					continue;
				}

				if (graphs[i] == null) {
					graphs[i] = buildGraphsFromRunDir(
							runDirs[i].getAbsolutePath()).get(0);
					graphs[i].dumpGraph("graph-files/" + graphs[i].progName
							+ graphs[i].pid + ".dot");
					// bigGraph = mergeGraph(bigGraph, graphs[i]);

				}
				if (graphs[j] == null) {
					graphs[j] = buildGraphsFromRunDir(
							runDirs[j].getAbsolutePath()).get(0);
					graphs[j].dumpGraph("graph-files/" + graphs[j].progName
							+ graphs[j].pid + ".dot");
					// bigGraph = mergeGraph(bigGraph, graphs[j]);
				}
				// if (graphs[i].progName.equals(graphs[j].progName))
				// continue;
				ExecutionGraph mergedGraph;
				if (graphs[i].nodes.size() < graphs[j].nodes.size()) {
					System.out.println("Comparison between "
							+ graphs[j].progName + graphs[j].pid + " & "
							+ graphs[i].progName + graphs[i].pid);
					mergedGraph = mergeGraph(graphs[j], graphs[i]);
				} else {
					System.out.println("Comparison between "
							+ graphs[i].progName + graphs[i].pid + " & "
							+ graphs[j].progName + graphs[j].pid);
					mergedGraph = mergeGraph(graphs[i], graphs[j]);
				}
				if (mergedGraph != null) {
					countMerged++;
				} else {
					countFailed++;
				}
			}
		}
		System.out.println("Successful Merging: " + countMerged);
		System.out.println("Failed Merging: " + countFailed);
	}
}
