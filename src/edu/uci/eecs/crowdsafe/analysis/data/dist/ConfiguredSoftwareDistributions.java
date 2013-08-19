package edu.uci.eecs.crowdsafe.analysis.data.dist;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.config.CrowdSafeAnalysisConfiguration;
import edu.uci.eecs.crowdsafe.analysis.loader.AutonomousSoftwareDistributionLoader;

public class ConfiguredSoftwareDistributions {

	public static synchronized ConfiguredSoftwareDistributions getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ConfiguredSoftwareDistributions();
			INSTANCE.initialize();
		}
		return INSTANCE;
	}

	private static ConfiguredSoftwareDistributions INSTANCE;

	private final File configDir;
	private final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();

	private ConfiguredSoftwareDistributions() {
		configDir = new File(CrowdSafeAnalysisConfiguration.getInstance()
				.getAnalysisHome(), "config");
	}

	private void initialize() {
		try {
			for (File configFile : configDir.listFiles()) {
				if (configFile.getName().endsWith(".asd")) {
					String distName = configFile.getName().substring(0,
							configFile.getName().lastIndexOf('.'));
					AutonomousSoftwareDistribution dist = AutonomousSoftwareDistributionLoader
							.loadDistribution(configFile);
					distributions.put(distName, dist);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(
					String.format(
							"Error reading the autonomous software distribution configuration from %s!",
							configDir.getAbsolutePath()));
		}
	}
}
