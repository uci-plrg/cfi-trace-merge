package edu.uci.eecs.crowdsafe.analysis.data.graph;

public enum EdgeType {
	INDIRECT,
	DIRECT,
	CALL_CONTINUATION,
	UNEXPECTED_RETURN,
	CROSS_MODULE,
	CROSS_CUSTOM_MODULE
}
