package edu.uci.eecs.crowdsafe.analysis.datasource;

public enum ProcessTraceStreamType {
	BLOCK_HASH("block-hash"),
	PAIR_HASH("pair-hash"),
	MODULE_GRAPH("bb-graph"),
	CROSS_MODULE_GRAPH("cross-module"),
	GRAPH_HASH("bb-graph-hash"),
	MODULE("module");
	
	public final String id;
	
	private ProcessTraceStreamType(String id) {
		this.id = id;
	}
}
