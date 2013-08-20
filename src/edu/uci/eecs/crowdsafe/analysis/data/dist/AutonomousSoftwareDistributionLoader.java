package edu.uci.eecs.crowdsafe.analysis.data.dist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


public class AutonomousSoftwareDistributionLoader {

	// TODO: should all the windows dlls really be in a single dist?
	public static AutonomousSoftwareDistribution loadDistribution(
			File configFile) throws IOException {
		Set<SoftwareDistributionUnit> distUnits = new HashSet<SoftwareDistributionUnit>();
		BufferedReader reader = new BufferedReader(new FileReader(configFile));
		String line;
		while ((line = reader.readLine()) != null) {
			distUnits.add(new SoftwareDistributionUnit(line.toLowerCase()));
		}
		String distName = configFile.getName().substring(0,
				configFile.getName().lastIndexOf('.'));
		return new AutonomousSoftwareDistribution(distName, distUnits);
	}
}
