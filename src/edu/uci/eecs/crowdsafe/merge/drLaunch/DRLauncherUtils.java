package edu.uci.eecs.crowdsafe.merge.drLaunch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class DRLauncherUtils {

	/**
	 * This function read the standard script file, which is supposed to have a nice structure in the file, and split
	 * the script into a few pieces according the server.config file so that the script can be executed in parallel on
	 * different machines
	 * 
	 * After calling this function, the splitted
	 * 
	 * @param scriptName
	 */
	public static void splitScript(String scriptName) {
		HashMap<String, Integer> serverInfo = DRConfiguration.getConfig()
				.getServerInfo();
		String generatedScriptsPath = DRConfiguration.getConfig()
				.getGeneratedScriptsPath();

		int subscriptNum = serverInfo.size();
		HashMap<String, StringBuilder> server2StrBuilder = new HashMap<String, StringBuilder>();
		// Initialize the string builder of each subscripts
		int totalProcessorNum = 0;
		for (String serverName : serverInfo.keySet()) {
			totalProcessorNum += serverInfo.get(serverName);
			server2StrBuilder.put(serverName, new StringBuilder());
		}

		List<String> lines = new LinkedList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(scriptName));

			int metaIndex0 = -1, metaIndex1 = -1, lineIdx = 0, headerEnd = -1, lastMetaRunIdx = -1;
			int executionSize = 0;
			String curLine;
			// Get the size of each execution body
			while ((curLine = br.readLine()) != null) {
				lineIdx++;
				lines.add(curLine);
				if (curLine.startsWith("# meta run ")) {
					lastMetaRunIdx = lineIdx;
					executionSize++;
				}
				if (curLine.equals("# meta run 0")) {
					headerEnd = lineIdx - 1;
					metaIndex0 = lineIdx;
				} else if (curLine.equals("# meta run 1")) {
					metaIndex1 = lineIdx;
				}
			}

			ListIterator<String> iter = lines.listIterator();
			// Copy the header of the script to all subscript string builders
			int cnt = 0;
			while (cnt < headerEnd) {
				cnt++;
				curLine = iter.next();
				for (String serverName : server2StrBuilder.keySet()) {
					server2StrBuilder.get(serverName).append(curLine + "\n");
				}
			}

			// Split the executions into many files
			int serverIdx = 0;
			for (String serverName : serverInfo.keySet()) {
				serverIdx++;
				StringBuilder strBuilder = server2StrBuilder.get(serverName);
				int processorNum = serverInfo.get(serverName);
				if (serverIdx != serverInfo.size()) {

					int subScriptsSize = (int) ((float) processorNum
							/ totalProcessorNum * executionSize);
					for (int i = 0; i < subScriptsSize; i++) {
						curLine = iter.next();
						strBuilder.append(curLine + "\n");
						while (!(curLine = iter.next())
								.startsWith("# meta run")) {
							if (curLine.startsWith("$runcs")
									|| curLine.startsWith("($runcs")) {
								strBuilder.append(curLine + " &\n");
							} else {
								strBuilder.append(curLine + "\n");
							}
						}
						iter.previous();
						if (i % processorNum == 0) {
							strBuilder.append("wait\n");
						}
					}
				} else {
					// Should read to the end
					cnt = 0;
					while (iter.hasNext()) {
						curLine = iter.next();
						if (curLine.startsWith("# meta run")) {
							cnt++;
							if (cnt % processorNum == 0) {
								strBuilder.append("wait\n");
							}
						}
						if (curLine.startsWith("$runcs")
								|| curLine.startsWith("($runcs")) {
							strBuilder.append(curLine + " &\n");
						} else {
							strBuilder.append(curLine + "\n");
						}
					}
				}
			}

			// Write pieces of files back to disks
			for (String serverName : serverInfo.keySet()) {
				String subscriptName = generatedScriptsPath + "/" + serverName
						+ "/";
				// + AnalysisUtil.getBaseNameFromPath(scriptName, "/");
				// TODO: file handling refactor
				Log.log("Writing " + subscriptName + " ...");
				writeSubScript(subscriptName, server2StrBuilder.get(serverName));
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static File[] getAllScripts() {
		String originalScriptsPath = DRConfiguration.getConfig()
				.getOriginalScriptsPath();
		File dir = new File(originalScriptsPath);
		File[] scripts = dir.listFiles();
		return scripts;
	}

	public static void splitScripts() {
		File[] scripts = getAllScripts();
		for (int i = 0; i < scripts.length; i++) {
			splitScript(scripts[i].getAbsolutePath());
		}
	}

	private static void writeSubScript(String fileName, StringBuilder strBuilder) {
		try {
			File file = new File(fileName);
			if (!file.exists()) {
				if (!file.getParentFile().mkdirs()) {
					Log.log("Can't create the necessary directories!");
				}
			}
			PrintWriter pw = new PrintWriter(fileName);
			pw.print(strBuilder.toString());
			pw.flush();
			pw.close();
			try {
				Runtime.getRuntime()
						.exec("chmod u+x " + file.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}
}
