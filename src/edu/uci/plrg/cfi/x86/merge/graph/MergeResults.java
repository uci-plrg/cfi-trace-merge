package edu.uci.plrg.cfi.x86.merge.graph;

import com.google.protobuf.GeneratedMessage;

import edu.uci.plrg.cfi.x86.graph.data.results.Graph;


public interface MergeResults {

	void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary);
	
	GeneratedMessage getResults();
	
	GraphMergeStrategy getStrategy();
}
