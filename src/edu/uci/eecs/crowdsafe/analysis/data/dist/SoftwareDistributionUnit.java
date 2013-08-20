package edu.uci.eecs.crowdsafe.analysis.data.dist;

public class SoftwareDistributionUnit {

	public static final SoftwareDistributionUnit UNKNOWN = new SoftwareDistributionUnit("__unknown__");
	
	public final String name;

	SoftwareDistributionUnit(String name) {
		this.name = name;
	}
}
