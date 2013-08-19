package edu.uci.eecs.crowdsafe.analysis.graph.representation;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

public class ModuleDescriptor implements Comparable {
	// The file that contains all the core modules known already
	public static final String CORE_MODULE_FILE = "/home/b/workspace/crowd-safe/cs-analysis-utils/config/core_modules.txt"; // TODO: load from env!
	
	public static HashSet<String> coreModuleNames;
	
	// Initialize the coreModuleNames set
	static {
		coreModuleNames = new HashSet<String>();
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(CORE_MODULE_FILE));
			String line;
			try {
				while ((line = br.readLine()) != null) {
					coreModuleNames.add(line.toLowerCase());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	
	public final String name;
	public final long beginAddr, endAddr;

	public ModuleDescriptor(String name, long beginAddr, long endAddr) {
		this.name = name.toLowerCase();
		this.beginAddr = beginAddr;
		this.endAddr = endAddr;
	}

	public String toString() {
		return name + ": 0x" + Long.toHexString(beginAddr) + " - 0x"
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
	public int compareTo(long addr) {
		if (addr > endAddr) {
			return -1;
		} else if (addr < beginAddr) {
			return 1;
		} else {
			return 0;
		}
	}
}
