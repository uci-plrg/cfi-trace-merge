package analysis.graph.representation;

import utils.AnalysisUtil;

public class NormalizedTag {
	public final String moduleName;
	public final Long relativeTag;

	public NormalizedTag(Node n) {
		if (n.getMetaNodeType() == MetaNodeType.SIGNATURE_HASH) {
			this.moduleName = "";
			this.relativeTag = 0l;
		} else {
			String modName = AnalysisUtil.getModuleName(n);
			long relTag = AnalysisUtil.getRelativeTag(n);
			this.moduleName = modName;
			this.relativeTag = relTag;
		}
	}
	
	public NormalizedTag(String moduleName, long relativeTag) {
		this.moduleName = moduleName;
		this.relativeTag = relativeTag;
	}
	
	public String toString() {
		return moduleName + "_" + Long.toHexString(relativeTag);
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
