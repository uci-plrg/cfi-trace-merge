package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ModuleInstance implements Comparable<ModuleInstance> {
	public static ModuleInstance UNKNOWN = new ModuleInstance(
			SoftwareDistributionUnit.UNKNOWN, 0L, Long.MAX_VALUE, 0L, 0L, 0L);

	public final SoftwareDistributionUnit unit;
	public final long start;
	public final long end;
	public final long blockTimestamp;
	public final long edgeTimestamp;
	public final long crossModuleEdgeTimestamp;

	public ModuleInstance(SoftwareDistributionUnit unit, long start, long end,
			long blockTimestamp, long edgeTimestamp,
			long crossModuleEdgeTimestamp) {
		this.unit = unit;
		this.start = start;
		this.end = end;
		this.blockTimestamp = blockTimestamp;
		this.edgeTimestamp = edgeTimestamp;
		this.crossModuleEdgeTimestamp = crossModuleEdgeTimestamp;
	}

	public boolean containsTag(long tag) {
		return ((tag >= start) && (tag <= end));
	}

	/**
	 * Compare between two modules. Assume that the modules are from the same execution and they are disjoint.
	 * 
	 * @param other
	 * @return
	 */
	public int compareTo(ModuleInstance other) {
		return (int) (start - other.start);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((unit == null) ? 0 : unit.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ModuleInstance other = (ModuleInstance) obj;
		if (unit == null) {
			if (other.unit != null)
				return false;
		} else if (!unit.equals(other.unit))
			return false;
		return true;
	}

	public String toString() {
		return String.format("%s: 0x%x - 0x%x", unit.name, start, end);
	}
}
