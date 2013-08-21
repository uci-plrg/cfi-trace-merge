package edu.uci.eecs.crowdsafe.analysis.data.dist;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.config.CrowdSafeAnalysisConfiguration;

public class ConfiguredSoftwareDistributions {

	public static synchronized ConfiguredSoftwareDistributions getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ConfiguredSoftwareDistributions();
			INSTANCE.initialize();
		}
		return INSTANCE;
	}

	private static String getFilename(String unitName) {
		int dashIndex = unitName.lastIndexOf('-');
		if (dashIndex < 0)
			return unitName;
		return unitName.substring(0, dashIndex);
	}

	private static ConfiguredSoftwareDistributions INSTANCE;

	public static final String MAIN_PROGRAM = "<main-program>";

	private final File configDir;
	public final Map<String, AutonomousSoftwareDistribution> distributions = new HashMap<String, AutonomousSoftwareDistribution>();

	private ConfiguredSoftwareDistributions() {
		configDir = new File(CrowdSafeAnalysisConfiguration.getInstance()
				.getAnalysisHome(), "config");

		distributions.put(MAIN_PROGRAM, new AutonomousSoftwareDistribution(
				MAIN_PROGRAM));
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

	public SoftwareDistributionUnit establishUnit(String name) {
		for (AutonomousSoftwareDistribution dist : distributions.values()) {
			for (SoftwareDistributionUnit unit : dist.distributionUnits) {
				if (unit.name.equals(name)
						|| unit.name.equals(getFilename(name)))
					return unit;
			}
		}
		SoftwareDistributionUnit unit = new SoftwareDistributionUnit(name);
		distributions.get(MAIN_PROGRAM).distributionUnits.add(unit);
		return unit;
	}
}
