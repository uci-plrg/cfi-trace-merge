package analysis.drLaunch;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class Configuration {
	public final String DEFAULT_SCRIPT_HOME = "/home/peizhaoo/cs-analysis-utils/cs-analysis-utils/scripts";
	public final String DEFAULT_CONFIG_FILE = DEFAULT_SCRIPT_HOME + "/run_dr";
	public final String DEFAULT_ORIGINAL_SCRIPT_PATH = DEFAULT_SCRIPT_HOME + "/run_dr/launch-under-cs";
	public final String DEFAULT_GENERATED_SCRIPTS_PATH = DEFAULT_SCRIPT_HOME + "/run_dr/splitted-scripts";
	
	public final String configFile = DEFAULT_CONFIG_FILE;
	public final HashMap<String, Integer> server2ProcessorNum;
	public final String originalScriptsPath = DEFAULT_ORIGINAL_SCRIPT_PATH;
	public final String generatedScriptsPath = DEFAULT_GENERATED_SCRIPTS_PATH;

	public Configuration(String fileName) {
		server2ProcessorNum = new HashMap<String, Integer>();
		configFile = fileName;
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String curLine;
			while ((curLine = br.readLine()) != null) {
				if (curLine.startsWith("#"))
					continue;
				String key = curLine.substring(0, curLine.indexOf('=')),
						value = curLine.substring(curLine.indexOf('=') + 1);
				if (key.equals("Server_Info")) {
					if (!readServerInfo(value)) {
						System.out.println("Wrong configuration file for servers!");
					}
				} else if (key.equals("Generated_Script_Dir")) {
					generatedScriptsPath = value;
				} else {
					System.out.println("Unrecognized field in config file: " + key);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean readServerInfo(String fileName) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String curLine;
			while ((curLine = br.readLine()) != null) {
				if (curLine.startsWith("#"))
					continue;
				String serverName = curLine.substring(0, curLine.indexOf('\t')),
						processorNumStr = curLine.substring(curLine.indexOf('\t') + 1);
				int processorNum = 0;
				try {
					processorNum = Integer.parseInt(processorNumStr);
					if (server2ProcessorNum.containsKey(serverName)) {
						System.out.println("Duplicate servers in server config file!");
					} else {
						server2ProcessorNum.put(serverName, processorNum);
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
}
