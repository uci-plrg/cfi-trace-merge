package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ProcessExecutionModuleSet {

	private final Set<ModuleInstance> instances = new HashSet<ModuleInstance>();

	private final Multimap<SoftwareDistributionUnit, ModuleInstance> instancesByUnit = HashMultimap
			.create();

	public void add(ModuleInstance module) {
		instances.add(module);
		instancesByUnit.put(module.unit, module);
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
		for (ModuleInstance instance : instances) {
			if (instance.containsTag(tag)) {
				if ((instance.blockTimestamp <= tagIndex)
						&& ((activeModule == ModuleInstance.UNKNOWN) || (instance.blockTimestamp > activeModule.blockTimestamp)))
					activeModule = instance;
			}
		}
		return activeModule;
	}

	public ModuleInstance getModuleForLoadedEdge(long tag, long edgeIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (ModuleInstance instance : instances) {
			if (instance.containsTag(tag)) {
				if ((instance.edgeTimestamp <= edgeIndex)
						&& ((activeModule == ModuleInstance.UNKNOWN) || (instance.edgeTimestamp > activeModule.edgeTimestamp)))
					activeModule = instance;
			}
		}
		return activeModule;
	}

	public ModuleInstance getModuleForLoadedCrossModuleEdge(long tag,
			long edgeIndex) {
		ModuleInstance activeModule = ModuleInstance.UNKNOWN;
		for (ModuleInstance instance : instances) {
			if (instance.containsTag(tag)) {
				if ((instance.crossModuleEdgeTimestamp <= edgeIndex)
						&& ((activeModule == ModuleInstance.UNKNOWN) || (instance.crossModuleEdgeTimestamp > activeModule.crossModuleEdgeTimestamp)))
					activeModule = instance;
			}
		}
		return activeModule;
	}

	public Collection<ModuleInstance> getModule(SoftwareDistributionUnit unit) {
		return instancesByUnit.get(unit);
	}
}
