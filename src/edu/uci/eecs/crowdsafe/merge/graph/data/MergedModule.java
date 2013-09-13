package edu.uci.eecs.crowdsafe.merge.graph.data;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;

public class MergedModule extends SoftwareModule {

	public MergedModule(SoftwareDistributionUnit unit, String version) {
		super(unit, version);
	}
}
