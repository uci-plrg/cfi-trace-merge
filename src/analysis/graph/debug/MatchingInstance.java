package analysis.graph.debug;

/**
 * This represents a matching instance of nodes, which is
 * used in debug mode
 * @author peizhaoo
 *
 */
public class MatchingInstance {
	public final int level;
	public final int index1, index2;
	public final MatchingType matchingType;
	public final int parentIndex;
	
	public MatchingInstance(int level, int index1, int index2, MatchingType matchingType, int parentIndex) {
		this.level = level;
		this.index1 = index1;
		this.index2 = index2;
		this.matchingType = matchingType;
		this.parentIndex = parentIndex;
	}
}
