package analysis.graph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import utils.AnalysisUtil;

import analysis.graph.representation.ExecutionGraph;

public class GraphAnalyzer {
	
	public static void pairComparison(String dir) {
		File file = new File(dir);
		File[] runDirs = file.listFiles();
		ExecutionGraph[] graphs = new ExecutionGraph[runDirs.length];
		int countFailed = 0, countMerged = 0;

		for (int i = 0; i < runDirs.length; i++) {
			for (int j = i + 1; j < runDirs.length; j++) {
				if (runDirs[i].getName().indexOf("run") == -1
						|| runDirs[j].getName().indexOf("run") == -1) {
					continue;
				}

				if (graphs[i] == null) {
					graphs[i] = ExecutionGraph.buildGraphsFromRunDir(
							runDirs[i].getAbsolutePath()).get(0);
					GraphInfo.dumpGraph(graphs[i], "graph-files/" + graphs[i].getProgName()
							+ graphs[i].getPid() + ".dot");
					// bigGraph = mergeGraph(bigGraph, graphs[i]);

				}
				if (graphs[j] == null) {
					graphs[j] = ExecutionGraph.buildGraphsFromRunDir(
							runDirs[j].getAbsolutePath()).get(0);
					GraphInfo.dumpGraph(graphs[j], "graph-files/" + graphs[j].progName
							+ graphs[j].getPid() + ".dot");
					// bigGraph = mergeGraph(bigGraph, graphs[j]);
				}
				// if (graphs[i].progName.equals(graphs[j].progName))
				// continue;
				ExecutionGraph mergedGraph;
				if (graphs[i].getNodes().size() < graphs[j].getNodes().size()) {
					System.out.println("Comparison between "
							+ graphs[j].getProgName() + graphs[j].getPid() + " & "
							+ graphs[i].getProgName() + graphs[i].getPid());
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
