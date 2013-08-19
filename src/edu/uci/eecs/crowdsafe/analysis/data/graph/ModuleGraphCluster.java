package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ModuleGraphCluster {
	private final AutonomousSoftwareDistribution distribution; // null means the modules are discovered at runtime
	private final Map<SoftwareDistributionUnit, ModuleGraph> graphs = new HashMap<SoftwareDistributionUnit, ModuleGraph>();

	public ModuleGraphCluster() {
		distribution = null;
	}

	public ModuleGraphCluster(AutonomousSoftwareDistribution distribution) {
		this.distribution = distribution;
	}

	public ModuleGraph getModuleGraph(SoftwareDistributionUnit softwareUnit) {
		return graphs.get(softwareUnit);
	}
	
	public void addModule(ModuleGraph moduleGraph) {
		graphs.put(moduleGraph.softwareUnit, moduleGraph);
	}
}
