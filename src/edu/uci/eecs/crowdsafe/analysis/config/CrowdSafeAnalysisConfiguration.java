package edu.uci.eecs.crowdsafe.analysis.config;

import java.io.File;

public class CrowdSafeAnalysisConfiguration {

	public static synchronized CrowdSafeAnalysisConfiguration getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new CrowdSafeAnalysisConfiguration();
			INSTANCE.initialize();
		}
		return INSTANCE;
	}

	private static CrowdSafeAnalysisConfiguration INSTANCE;
	private static final String CROWD_SAFE_ANALYSIS_DIR = "CROWD_SAFE_ANALYSIS_DIR";

	private File ANALYSIS_HOME;

	private void initialize() {
		String homeDir = System.getProperty("CROWD_SAFE_ANALYSIS_DIR");
		if (homeDir == null)
			throw new IllegalStateException(
					String.format(
							"Please configure the environment variable %s with the home directory of the analysis program.",
							CROWD_SAFE_ANALYSIS_DIR));

		ANALYSIS_HOME = new File(homeDir);
		if (!ANALYSIS_HOME.exists())
			throw new IllegalStateException(String.format(
					"The configured %s cannot be found: %s",
					CROWD_SAFE_ANALYSIS_DIR, homeDir));
	}
	
	public File getAnalysisHome() {
		return ANALYSIS_HOME;
	}
}
