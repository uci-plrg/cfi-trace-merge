package analysis.graph.representation;

/**
 * <p>
 * This is an abstraction of the actual tag of re-writable programs. Within each
 * execution, tag together with version number uniquely identifies a node in a
 * graph. 
 * </p>
 * 
 * @author peizhaoo
 * 
 */
public class VersionedTag {
	public static final VersionedTag voidTag = new VersionedTag(-1, 0);
	
	public final long tag;
	public final int versionNumber;

	public VersionedTag(long tag, int versionNumber) {
		this.tag = tag;
		this.versionNumber = versionNumber;
	}
	
	public String toString() {
		return Long.toHexString(tag) + "-" + versionNumber;
	}

	public boolean equals(Object another) {
		if (another == null) {
			return false;
		}
		if (another.getClass() != VersionedTag.class) {
			return false;
		}

		VersionedTag t = (VersionedTag) another;
		if (t.tag == tag && t.versionNumber == versionNumber) {
			return true;
		} else {
			return false;
		}
	}

	public int hashCode() {
		return ((Long) tag).hashCode() >> 5 ^ ((Integer) versionNumber).hashCode();
	}
}
