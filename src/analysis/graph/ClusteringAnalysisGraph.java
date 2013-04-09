package analysis.graph;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import analysis.graph.representation.ExecutionGraph;

import utils.AnalysisUtil;

import gnu.getopt.Getopt;


public class ClusteringAnalysisGraph {
	ExecutionGraph graph;
	
	ExecutionGraph[] graphs;
	String[] runPaths;

	public ClusteringAnalysisGraph(String tagFileName, String lookupFileName) {
		
	}
	
	public ClusteringAnalysisGraph(String tagFileName, String lookupFileName, String blockFileName) {
		
	}
	
	public ClusteringAnalysisGraph(String runDir) {
		
	}
	// string lines in 'fileName' should be in the format of
	// <progName>:<hash-of-first-main-block>
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
	
	public static void analyzeAllFirstMainBlock(String dirName) {
		ArrayList<String> runDirs = AnalysisUtil.getAllRunDirs(dirName);
		HashMap<Long, String> firstMainHashes = new HashMap<Long, String>(); 
		for (String runDir : runDirs) {
			//ExecutionGraph g = ExecutionGraph.buildGraphFromRunDir(runDir);
			ExecutionGraph g = null;
			if (!g.isValidGraph()) {
				System.out.println(runDir + " : invalid graph!");
				continue;
			}
			long firstMainHash = GraphInfo.outputFirstMain(g);
			//String progName = AnalysisUtil.getProgNameFromPath(runDir);
			if (!firstMainHashes.containsKey(firstMainHash)) {
				firstMainHashes.put(firstMainHash, runDir);
			}
		}
		for (long l : firstMainHashes.keySet()) {
			System.out.println(Long.toHexString(l) + ":" + firstMainHashes.get(l));
		}
	}

	public static void printUsage() {
		System.out.println("Usage of ClusteringAnalysisGraph:");
		System.out
				.println("java [-cp <Class_Path>] ClusteringAnalysisGraph -t <tagFile> -l <lookupFile>");
	}
	
	public void dumpGraph(String fileName) {
		GraphInfo.dumpGraph(graph, fileName);
	}

	public static void main(String[] argvs) {
		Getopt g = new Getopt("ClusteringAnalysisGraph", argvs, "t:l:g:m:r:d:b:");
		int opt = 0;
		String tagFile = null, lookupFile = null, blockFile = null;
		String graphFileName = null;
		String firstMainFile = null;
		String runDirs = null;
		String runDir = null;
		boolean error = false;
		while ((opt = g.getopt()) != -1) {
			switch (opt) {
			case 't':
				tagFile = g.getOptarg();
				break;
			case 'l':
				lookupFile = g.getOptarg();
				break;
			case 'b':
				blockFile = g.getOptarg();
				break;
			case 'd':
				// in this case, only provide the run directory
				// instead of the tagFile and lookupFile 
				runDir = g.getOptarg();
				break;
			case 'g':
				graphFileName = g.getOptarg();
				break;
			case 'm':
				firstMainFile = g.getOptarg();
				break;
			case 'r':
				runDirs = g.getOptarg();
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
		if ((tagFile != null && lookupFile != null) || runDir != null) {
			if (runDir == null) {
				analysis = new ClusteringAnalysisGraph(tagFile, lookupFile, blockFile);
			} else {
				analysis = new ClusteringAnalysisGraph(runDir);
			}
		}
		if (graphFileName != null && analysis != null)
			analysis.dumpGraph(graphFileName);
		if (firstMainFile != null) {
			analyzeFirstMainBlock(firstMainFile);
		}
		if (runDirs != null) {
			analyzeAllFirstMainBlock(runDirs);
		}
	}

}
