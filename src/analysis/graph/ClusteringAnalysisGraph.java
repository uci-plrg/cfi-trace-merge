package analysis.graph;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import utils.AnalysisUtil;

import gnu.getopt.Getopt;

public class ClusteringAnalysisGraph {
	ExecutionGraph graph;
	
	ExecutionGraph[] graphs;
	String[] runPaths;

	public ClusteringAnalysisGraph(String tagFileName, String lookupFileName) {
		this.graph = new ExecutionGraph(tagFileName, lookupFileName);
	}
	
	private ExecutionGraph buildGraphFromRunDir(String path) {
		ExecutionGraph g;
		File runDir = new File(path);
//		for (File f : runDir.listFiles()) {
//			if (f.getName().indexOf("") != -1) {
//				
//			} else if
//		}
		return null;
	}
	
	public static void analyzeFirstMainBlock(String fileName) {
		ArrayList<String> lines = AnalysisUtil.getStringPerline(fileName);
		HashMap<Long, String> blocks = new HashMap<Long, String>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int colonPos = line.indexOf(":"); 
			if (colonPos != line.length() - 1) {
				String progName = line.substring(0, colonPos);
				String hashStr = line.substring(colonPos + 1);
				BigInteger bigInt = new BigInteger(hashStr, 16);
				long hash = bigInt.longValue();
				if (blocks.containsKey(hash)) {
					if (!progName.equals(blocks.get(hash))) 
						//System.out.println(progName + " & " + blocks.get(hash) + " -> " + hash);
						System.out.println(progName + ":" + blocks.get(hash));
				} else {
					blocks.put(hash, progName);
				}
			}
		}
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
		Getopt g = new Getopt("ClusteringAnalysisGraph", argvs, "t:l:g:m:");
		int opt = 0;
		String tagFile = null, lookupFile = null;
		String graphFileName = null;
		String firstMainFile = null;
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
			case 'm':
				firstMainFile = g.getOptarg();
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
		ClusteringAnalysisGraph analysis = null;
		if (tagFile != null && lookupFile != null)
			analysis = new ClusteringAnalysisGraph(tagFile, lookupFile);
		if (graphFileName != null && analysis != null)
			analysis.dumpGraph(graphFileName);
		if (firstMainFile != null) {
			analyzeFirstMainBlock(firstMainFile);
		}
	}

}
