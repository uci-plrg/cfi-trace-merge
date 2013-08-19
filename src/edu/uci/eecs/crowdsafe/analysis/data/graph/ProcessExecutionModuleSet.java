package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ProcessExecutionModuleSet {

	private final Map<String, ModuleDescriptor> descriptors = new HashMap<String, ModuleDescriptor>();

	public void add(ModuleDescriptor module) {
		descriptors.put(module.name, module);
	}

	public boolean hashOverlap() {
		List<ModuleDescriptor> list = new ArrayList<ModuleDescriptor>();
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				ModuleDescriptor mod1 = list.get(i), mod2 = list.get(j);
				if ((mod1.beginAddr < mod2.beginAddr && mod1.endAddr > mod2.beginAddr)
						|| (mod1.beginAddr < mod2.endAddr && mod1.endAddr > mod2.endAddr)) {
					return true;
				}
			}
		}
		return false;
	}

	public SoftwareDistributionUnit getSoftwareUnit(long tag) {
		for (ModuleDescriptor descriptor : descriptors.values()) {
			if (descriptor.containsTag(tag)) {
				return descriptor;
			}
		}
		return SoftwareDistributionUnit.UNKNOWN;
	}
}
