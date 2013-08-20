package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ModuleGraphCluster {
	public final AutonomousSoftwareDistribution distribution; 
	private final Map<SoftwareDistributionUnit, ModuleGraph> graphs = new HashMap<SoftwareDistributionUnit, ModuleGraph>();

	public ModuleGraphCluster(AutonomousSoftwareDistribution distribution) {
		this.distribution = distribution;
	}

	public ModuleGraph getModuleGraph(SoftwareDistributionUnit softwareUnit) {
		return graphs.get(softwareUnit);
	}

	public void addModule(ModuleGraph moduleGraph) {
		graphs.put(moduleGraph.softwareUnit, moduleGraph);
	}
	
	public Collection<ModuleGraph> getGraphs() {
		return graphs.values();
	}
}
