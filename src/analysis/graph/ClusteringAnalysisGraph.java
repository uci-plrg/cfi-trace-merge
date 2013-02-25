package analysis.graph;

import gnu.getopt.Getopt;

import java.util.ArrayList;
import java.util.HashMap;

import analysis.graph.ExecutionGraph.Node;

import utils.GraphAnalysisUtil;

public class ClusteringAnalysisGraph {
	ExecutionGraph graph;

	public ClusteringAnalysisGraph(String tagFileName, String lookupFileName) {
		this.graph = new ExecutionGraph(tagFileName, lookupFileName);
	}

	public static void printUsage() {
		System.out.println("Usage of ClusteringAnalysisGraph:");
		System.out
				.println("java [-cp <Class_Path>] ClusteringAnalysisGraph -t <tagFile> -l <lookupFile>");
	}
	
	public void dumpGraph(String fileName) {
		graph.dumpGraph(fileName);
	}

	public static void main(String[] argvs) {
		Getopt g = new Getopt("ClusteringAnalysisGraph", argvs, "t:l:g:");
		int opt = 0;
		String tagFile = null, lookupFile = null;
		String graphFileName = null;
		boolean error = false;
		while ((opt = g.getopt()) != -1) {
			switch (opt) {
			case 't':
				tagFile = g.getOptarg();
				break;
			case 'l':
				lookupFile = g.getOptarg();
				break;
			case 'g':
				graphFileName = g.getOptarg();
				break;
			case '?':
				error = true;
				System.out.println("Option parse error happened in option -"
						+ (char) g.getOptopt());
				printUsage();
				break;
			default:
				System.out.println("Unknown option parsing error!");
				printUsage();
				break;
			}
		}
		if (error)
			return;
		ClusteringAnalysisGraph analysis = new ClusteringAnalysisGraph(tagFile, lookupFile);
		analysis.dumpGraph(graphFileName);
	}

}
