package analysis;

import java.math.BigInteger;
import java.util.HashSet;

import utils.AnalysisUtil;

public class HashCollisionAnalysis {
	static public class BlockInstructions {
		//private ArrayList<String>
	}
	
	public static void main(String[] argvs) {
		HashSet<Long> set = AnalysisUtil.getSetFromRunDir(argvs[0]);
		BigInteger hash = new BigInteger(argvs[1], 16);
		if (set.contains(hash.longValue())) {
			System.out.println("It contains hash value " + Long.toHexString(hash.longValue()));
		} else {
			System.out.println("It does not contain hash value " + Long.toHexString(hash.longValue()));
		}
	}
}
