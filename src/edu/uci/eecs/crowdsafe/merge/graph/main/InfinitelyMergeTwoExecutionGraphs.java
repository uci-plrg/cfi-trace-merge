package edu.uci.eecs.crowdsafe.merge.graph.main;

import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;

public class InfinitelyMergeTwoExecutionGraphs {

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		CommonMergeOptions options = new CommonMergeOptions(stack);
		MergeTwoGraphs executor = new MergeTwoGraphs(options);
		executor.run(stack, Integer.MAX_VALUE);
	}
}
