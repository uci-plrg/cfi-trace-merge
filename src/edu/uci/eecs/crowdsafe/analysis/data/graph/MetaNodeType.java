package edu.uci.eecs.crowdsafe.analysis.data.graph;

public enum MetaNodeType {
	NORMAL,
	ENTRY,
	EXIT,
	TRAMPOLINE,
	RETURN,
	SIGNAL_HANDLER,
	SIGRETURN,
	HASH_PLACEHOLDER,
	SIGNATURE_HASH,
	MODULE_BOUNDARY
}
