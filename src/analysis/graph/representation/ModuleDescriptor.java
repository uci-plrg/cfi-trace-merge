package analysis.graph.representation;

public class ModuleDescriptor implements Comparable {
	public final String name;
	public final long beginAddr, endAddr;
	
	public ModuleDescriptor(String name, long beginAddr, long endAddr) {
		this.name = name;
		this.beginAddr = beginAddr;
		this.endAddr = endAddr;
	}
	
	/**
	 * Compare between two modules. Assume that the modules are from the
	 * same execution and they are disjoint.
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
