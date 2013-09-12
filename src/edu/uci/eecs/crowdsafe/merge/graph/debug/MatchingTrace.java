package edu.uci.eecs.crowdsafe.merge.graph.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.log.Log;

/**
 * This is to record how a trace of nodes are matched so that we can find out the mismatch or conflict more easily
 * 
 * @author peizhaoo
 * 
 */
public class MatchingTrace {
	List<MatchingInstance> matchingInstances = new ArrayList<MatchingInstance>();
	Map<Node.Key, MatchingInstance> key2MatchingInstance = new HashMap<Node.Key, MatchingInstance>();

	public void addInstance(MatchingInstance instance) {
		matchingInstances.add(instance);
		key2MatchingInstance.put(instance.rightKey, instance);
	}

	public void printTrace(int index) {
		MatchingInstance inst = key2MatchingInstance.get(index);
		Log.log(inst.matchingType + ":" + inst.leftKey + "<->" + inst.rightKey);
		while (inst.parentKey != null) {
			inst = key2MatchingInstance.get(inst.parentKey);
			MatchingInstance parentInst = key2MatchingInstance.get(inst.parentKey);
			Node.Key leftParentKey = parentInst == null ? null : parentInst.leftKey;
			Node.Key parentIdx2 = parentInst == null ? null : parentInst.rightKey;
			Log.log(inst.matchingType + ":" + inst.leftKey + "<->" + inst.rightKey + "(By " + leftParentKey + "<->"
					+ parentIdx2 + ")");
		}
	}

	public void printTrace() {
		for (int i = 0; i < matchingInstances.size(); i++) {
			MatchingInstance inst = matchingInstances.get(i);
			Log.log(inst.matchingType + ":" + inst.leftKey + "<->" + inst.rightKey);
		}
	}

}
