package edu.uci.eecs.crowdsafe.merge.graph;

import com.google.protobuf.GeneratedMessage;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;

public interface MergeResults {

	void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary);

	GeneratedMessage getResults();
}
