package analysis.drLaunch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import utils.AnalysisUtil;

public class DRLauncherUtils {

	/**
	 * This function read the standard script file, which is supposed to have a
	 * nice structure in the file, and split the script into a few pieces
	 * according the server.config file so that the script can be executed in
	 * parallel on different machines
	 * 
	 * After calling this function, the splitted
	 * 
	 * @param scriptName
	 */
	public static void splitScript(String scriptName) {
		HashMap<String, Integer> serverInfo = Configuration.getConfig()
				.getServerInfo();
		String generatedScriptsPath = Configuration.getConfig()
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
					
					int subScriptsSize = (int) ((float) processorNum / totalProcessorNum);
					for (int i = 0; i < subScriptsSize; i++) {
						strBuilder.append(iter.next());
						while (!(curLine = iter.next()).startsWith("# meta run")) {
							if (curLine.startsWith("$runcs") || curLine.startsWith("($runcs")) {
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
						if (!curLine.startsWith("# meta run")) {
							cnt++;
							if (cnt % processorNum == 0) {
								strBuilder.append("wait\n");
							}
						}
						if (curLine.startsWith("$runcs") || curLine.startsWith("($runcs")) {
							strBuilder.append(curLine + " &\n");
						} else {
							strBuilder.append(curLine + "\n");
						}
					}
				}
			}

			// Write pieces of files back to disks
			for (String serverName : serverInfo.keySet()) {
				writeSubScript(generatedScriptsPath + "/machine-" + serverName + "/"
						+ AnalysisUtil.getBaseNameFromPath(scriptName, "/"), server2StrBuilder.get(serverName));
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeSubScript(String fileName, StringBuilder strBuilder) {
		try {
			File file = new File(fileName);
			if (!file.exists()) {
				if (!file.mkdirs()) {
					System.out.println("Can't create the necessary directories!");
				}
			}
			PrintWriter pw = new PrintWriter(fileName);
			pw.print(strBuilder.toString());
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] argvs) {
		String originalScriptPath = Configuration.getConfig()
				.getOriginalScriptsPath();
		splitScript(originalScriptPath + "/dd-under-cs");
	}
}
