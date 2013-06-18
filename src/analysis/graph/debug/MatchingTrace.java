package analysis.graph.debug;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This is to record how a trace of nodes are matched so that we can find out
 * the mismatch or conflict more easily
 * 
 * @author peizhaoo
 * 
 */
public class MatchingTrace {
	ArrayList<MatchingInstance> matchingInstances;
	HashMap<Integer, MatchingInstance> index2MatchingInstance;

	public MatchingTrace() {
		matchingInstances = new ArrayList<MatchingInstance>();
		index2MatchingInstance = new HashMap<Integer, MatchingInstance>();
	}

	public void addInstance(MatchingInstance instance) {
		matchingInstances.add(instance);
		index2MatchingInstance.put(instance.index2, instance);
	}

	public void printTrace(int index) {
		MatchingInstance inst = index2MatchingInstance.get(index);
		System.out.println(inst.level + ":" + inst.matchingType + ":"
				+ inst.index1 + "<->" + inst.index2);
		while (inst.parentIndex != -1) {
			inst = index2MatchingInstance.get(inst.parentIndex);
			MatchingInstance parentInst = index2MatchingInstance
					.get(inst.parentIndex);
			int parentIdx1 = parentInst == null ? -1 : parentInst.index1, parentIdx2 = parentInst == null ? -1
					: parentInst.index2;
			System.out.println(inst.level + ":" + inst.matchingType + ":"
					+ inst.index1 + "<->" + inst.index2 + "(By " + parentIdx1
					+ "<->" + parentIdx2 + ")");
		}
	}

	public void printTrace() {
		for (int i = 0; i < matchingInstances.size(); i++) {
			MatchingInstance inst = matchingInstances.get(i);
			System.out.println(inst.level + ":" + inst.matchingType + ":"
					+ inst.index1 + "<->" + inst.index2);
		}
	}

}
