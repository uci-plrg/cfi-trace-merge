package edu.uci.eecs.crowdsafe.analysis.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class AutonomousSoftwareDistributionLoader {

	public static AutonomousSoftwareDistribution loadDistribution(
			File configFile) throws IOException {
		Set<SoftwareDistributionUnit> distUnits = new HashSet<SoftwareDistributionUnit>();
		BufferedReader reader = new BufferedReader(new FileReader(configFile));
		String line;
		while ((line = reader.readLine()) != null) {
			distUnits.add(new SoftwareDistributionUnit(line.toLowerCase()));
		}
		return new AutonomousSoftwareDistribution(distUnits);
	}
}
