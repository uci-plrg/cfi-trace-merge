package analysis.graph.representation;

public class NormalizedTag {
	public final String moduleName;
	public final Long relativeTag;

	public NormalizedTag(String moduleName, long relativeTag) {
		this.moduleName = moduleName;
		this.relativeTag = relativeTag;
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (o.getClass() != NormalizedTag.class) {
			return false;
		}
		NormalizedTag another = (NormalizedTag) o;
		if (another.moduleName.equals(moduleName)
				&& another.relativeTag.equals(relativeTag)) {
			return true;
		} else {
			return false;
		}
	}

	public int hashCode() {
		return moduleName.hashCode() << 5 ^ relativeTag.hashCode();
	}
}
