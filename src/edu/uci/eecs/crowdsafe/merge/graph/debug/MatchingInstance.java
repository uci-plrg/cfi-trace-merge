package edu.uci.eecs.crowdsafe.merge.graph.debug;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;

/**
 * This represents a matching instance of nodes, which is used in debug mode
 * 
 * @author peizhaoo
 * 
 */
public class MatchingInstance {
	public final int level;
	public final Node.Key leftKey, rightKey;
	public final MatchingType matchingType;
	public final Node.Key parentKey;

	public MatchingInstance(int level, Node.Key leftKey, Node.Key rightKey, MatchingType matchingType,
			Node.Key parentKey) {
		this.level = level;
		this.leftKey = leftKey;
		this.rightKey = rightKey;
		this.matchingType = matchingType;
		this.parentKey = parentKey;
	}
}
