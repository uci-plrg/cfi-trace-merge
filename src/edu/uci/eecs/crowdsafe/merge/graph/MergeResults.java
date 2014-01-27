package edu.uci.eecs.crowdsafe.merge.graph;

import com.google.protobuf.GeneratedMessage;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.data.results.Graph.Process;

public interface MergeResults {

	void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary);
	
	GeneratedMessage getResults();
	
	GraphMergeStrategy getStrategy();
}
