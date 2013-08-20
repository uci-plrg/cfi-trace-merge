package edu.uci.eecs.crowdsafe.analysis.data.dist;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AutonomousSoftwareDistribution {
	public final String name;
	public final Set<SoftwareDistributionUnit> distributionUnits;

	public AutonomousSoftwareDistribution(String name) {
		this.name = name;
		distributionUnits = new HashSet<SoftwareDistributionUnit>();
	}

	public AutonomousSoftwareDistribution(String name,
			Set<SoftwareDistributionUnit> distributionUnits) {
		this.name = name;
		this.distributionUnits = Collections.unmodifiableSet(distributionUnits);
	}
}
