package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ProcessExecutionModuleSet {

	private final Map<SoftwareDistributionUnit, ModuleInstance> descriptors = new HashMap<SoftwareDistributionUnit, ModuleInstance>();

	public void add(ModuleInstance module) {
		descriptors.put(module.unit, module);
	}

	public boolean hashOverlap() {
		List<ModuleInstance> list = new ArrayList<ModuleInstance>();
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				ModuleInstance mod1 = list.get(i), mod2 = list.get(j);
				if ((mod1.start < mod2.start && mod1.end > mod2.start)
						|| (mod1.start < mod2.end && mod1.end > mod2.end)) {
					return true;
				}
			}
		}
		return false;
	}

	public ModuleInstance getModuleForLoadedBlock(long tag, long tagIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (ModuleInstance descriptor : descriptors.values()) {
			if (descriptor.containsTag(tag)) {
				if ((descriptor.blockTimestamp < tagIndex)
						&& ((activeModule == ModuleInstance.UNKNOWN) || (descriptor.blockTimestamp > activeModule.blockTimestamp)))
					activeModule = descriptor;
			}
		}
		return activeModule;
	}

	public ModuleInstance getModuleForLoadedEdge(long tag, long edgeIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (ModuleInstance descriptor : descriptors.values()) {
			if (descriptor.containsTag(tag)) {
				if ((descriptor.edgeTimestamp < edgeIndex)
						&& ((activeModule == ModuleInstance.UNKNOWN) || (descriptor.edgeTimestamp > activeModule.edgeTimestamp)))
					activeModule = descriptor;
			}
		}
		return activeModule;
	}

	public ModuleInstance getModuleForLoadedCrossModuleEdge(long tag,
			long edgeIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (ModuleInstance descriptor : descriptors.values()) {
			if (descriptor.containsTag(tag)) {
				if ((descriptor.crossModuleEdgeTimestamp < edgeIndex)
						&& ((activeModule == ModuleInstance.UNKNOWN) || (descriptor.crossModuleEdgeTimestamp > activeModule.crossModuleEdgeTimestamp)))
					activeModule = descriptor;
			}
		}
		return activeModule;
	}

	public ModuleInstance getModule(SoftwareDistributionUnit unit) {
		return descriptors.get(unit);
	}
}
