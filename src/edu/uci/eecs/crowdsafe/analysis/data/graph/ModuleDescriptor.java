package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.HashSet;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ModuleDescriptor implements Comparable<ModuleDescriptor> {
	public static ModuleDescriptor UNKNOWN = new ModuleDescriptor(
			SoftwareDistributionUnit.UNKNOWN, 0L, 0L);

	public final SoftwareDistributionUnit unit;
	public final long beginAddr;
	public final long endAddr;

	public ModuleDescriptor(SoftwareDistributionUnit unit, long beginAddr,
			long endAddr) {
		this.unit = unit;
		this.beginAddr = beginAddr;
		this.endAddr = endAddr;
	}

	public boolean containsTag(long addr) {
		return ((addr >= beginAddr) && (addr <= endAddr));
	}

	/**
	 * Compare between two modules. Assume that the modules are from the same execution and they are disjoint.
	 * 
	 * @param other
	 * @return
	 */
	public int compareTo(ModuleDescriptor other) {
		return (int) (beginAddr - other.beginAddr);
	}

	public String toString() {
		return unit.name + ": 0x" + Long.toHexString(beginAddr) + " - 0x"
				+ Long.toHexString(endAddr);
	}
}
