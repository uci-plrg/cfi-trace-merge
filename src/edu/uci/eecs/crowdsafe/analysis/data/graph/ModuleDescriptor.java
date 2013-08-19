package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

public class ModuleDescriptor implements Comparable {
	public static HashSet<String> coreModuleNames;
	
	public final SoftwareDistributionUnit unit;
	public final long beginAddr, endAddr;

	public ModuleDescriptor(SoftwareDistributionUnit unit, long beginAddr, long endAddr) {
		this.unit = unit;
		this.beginAddr = beginAddr;
		this.endAddr = endAddr;
	}

	public String toString() {
		return unit.name + ": 0x" + Long.toHexString(beginAddr) + " - 0x"
				+ Long.toHexString(endAddr);
	}

	/**
	 * Compare between two modules. Assume that the modules are from the same
	 * execution and they are disjoint.
	 * 
	 * @param anotherMod
	 * @return
	 */
	public int compareTo(Object o) {
		ModuleDescriptor anotherMod = (ModuleDescriptor) o;
		if (beginAddr < anotherMod.beginAddr) {
			return -1;
		} else if (beginAddr > anotherMod.beginAddr) {
			return 1;
		} else {
			return 0;
		}
	}

	/**
	 * Test where the given address locates relative to current module
	 * 
	 * @param addr
	 * @return
	 */
	public boolean containsTag(long addr) {
		if (addr > endAddr) {
			return false;
		} else if (addr < beginAddr) {
			return false;
		} else {
			return true;
		}
	}
}
