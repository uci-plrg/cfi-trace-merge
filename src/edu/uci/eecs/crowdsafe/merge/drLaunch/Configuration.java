package edu.uci.eecs.crowdsafe.merge.drLaunch;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class Configuration {
	public final String DEFAULT_SCRIPT_HOME = "/home/peizhao/cs-analysis-utils/cs-analysis-utils/scripts";
	public final String DEFAULT_RUNDR_HOME = DEFAULT_SCRIPT_HOME + "/run-dr";
	public final String DEFAULT_CONFIG_FILE = DEFAULT_RUNDR_HOME + "/config";
	public final String DEFAULT_ORIGINAL_SCRIPT_PATH = DEFAULT_RUNDR_HOME
			+ "/launch-under-cs";
	public final String DEFAULT_GENERATED_SCRIPTS_PATH = DEFAULT_RUNDR_HOME
			+ "/splitted-scripts";

	private String configFile = DEFAULT_CONFIG_FILE;
	private HashMap<String, Integer> server2ProcessorNum;
	private String originalScriptsPath = DEFAULT_ORIGINAL_SCRIPT_PATH;
	private String generatedScriptsPath = DEFAULT_GENERATED_SCRIPTS_PATH;

	private static Configuration config = null;

	public HashMap<String, Integer> getServerInfo() {
		return this.server2ProcessorNum;
	}

	public String getOriginalScriptsPath() {
		return this.originalScriptsPath;
	}

	public String getGeneratedScriptsPath() {
		return this.generatedScriptsPath;
	}

	// Singleton of configuration file
	public static Configuration getConfig(String... fileName) {
		if (config == null) {
			return new Configuration(fileName);
		} else {
			return config;
		}
	}

	private Configuration(String... fileName) {
		if (fileName.length == 0) {
			configFile = DEFAULT_CONFIG_FILE;
		} else {
			configFile = fileName[0];
		}
		server2ProcessorNum = new HashMap<String, Integer>();

		try {
			BufferedReader br = new BufferedReader(new FileReader(configFile));
			String curLine;
			while ((curLine = br.readLine()) != null) {
				if (curLine.startsWith("#"))
					continue;
				String key = curLine.substring(0, curLine.indexOf('=')), value = curLine
						.substring(curLine.indexOf('=') + 1);
				if (key.equals("Server_Info")) {
					if (!readServerInfo(DEFAULT_RUNDR_HOME + "/" + value)) {
						Log.log("Wrong configuration file for servers!");
					}
				} else if (key.equals("Generated_Script_Dir")) {
					generatedScriptsPath = value;
				} else {
					Log.log("Unrecognized field in config file: " + key);
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
				if (curLine.startsWith("#") || curLine.startsWith("\n"))
					continue;
				String serverName = curLine.substring(0, curLine.indexOf('\t')), processorNumStr = curLine
						.substring(curLine.indexOf('\t') + 1);
				int processorNum = 0;
				try {
					processorNum = Integer.parseInt(processorNumStr);
					if (server2ProcessorNum.containsKey(serverName)) {
						Log.log("Duplicate servers in server config file!");
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
