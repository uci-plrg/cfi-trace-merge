package edu.uci.eecs.crowdsafe.analysis.data.dist;

import java.util.Set;

public class AutonomousSoftwareDistribution {
	public final Set<SoftwareDistributionUnit> distributionUnits;

	public AutonomousSoftwareDistribution(
			Set<SoftwareDistributionUnit> distributionUnits) {
		this.distributionUnits = distributionUnits;
	}
}
