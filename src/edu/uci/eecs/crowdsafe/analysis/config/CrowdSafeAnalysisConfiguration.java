package edu.uci.eecs.crowdsafe.analysis.config;

import java.io.File;

import edu.uci.eecs.crowdsafe.util.log.Log;

public class CrowdSafeAnalysisConfiguration {

	public static synchronized CrowdSafeAnalysisConfiguration getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new CrowdSafeAnalysisConfiguration();
			INSTANCE.initializeImpl();
		}
		return INSTANCE;
	}
	
	public static void initialize() {
		getInstance();
	}

	private static CrowdSafeAnalysisConfiguration INSTANCE;
	private static final String CROWD_SAFE_MERGE_DIR = "CROWD_SAFE_MERGE_DIR";

	private File ANALYSIS_HOME;

	private void initializeImpl() {
		String homeDir = System.getenv(CROWD_SAFE_MERGE_DIR);
		if (homeDir == null)
			throw new IllegalStateException(
					String.format(
							"Please configure the environment variable %s with the home directory of the analysis program.",
							CROWD_SAFE_MERGE_DIR));

		ANALYSIS_HOME = new File(homeDir);
		if (!ANALYSIS_HOME.exists())
			throw new IllegalStateException(String.format(
					"The configured %s cannot be found: %s",
					CROWD_SAFE_MERGE_DIR, homeDir));

		Log.addOutput(System.out);
	}

	public File getAnalysisHome() {
		return ANALYSIS_HOME;
	}
}
