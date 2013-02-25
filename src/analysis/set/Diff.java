package analysis.set;

import gnu.getopt.Getopt;

import java.io.*;
import java.util.*;
import java.lang.Math;
import java.math.BigInteger;
import java.nio.*;

import utils.AnalysisUtil;

public class Diff {

	public void diff(String filename1, String filename2, int showNewHashes) {
		HashSet<Long> set1 = AnalysisUtil.initSetFromFile(filename1), set2 = AnalysisUtil
				.initSetFromFile(filename2);
		
		if (set1.containsAll(set2) && set2.containsAll(set1))
			System.out.println("No new hash codes found in set " + filename1 + "!");
		else {
			System.out.println("The two sets are not the same!");
			HashSet<Long> s1_s2 = AnalysisUtil.minus(set1, set2), s2_s1 = AnalysisUtil
					.minus(set2, set1);
			System.out.println("set1: " + filename1);
			System.out.println("size of set1: " + set1.size());
			System.out.println("set2: " + filename2);
			System.out.println("size of set2: " + set2.size());
			System.out.println("intersection size = "
					+ AnalysisUtil.intersection(set1, set2).size());
			System.out.println("Number of new hash codes: " + s1_s2.size());
			if (showNewHashes == 1) {
				for (Long l : s1_s2) {
					System.out.println(Long.toHexString(l));
				}
			}
			// System.out.println("Minus of set2 and set1 is " + s2_s1.size());
			// for (Long l : s2_s1) {
			// System.out.println(Long.toHexString(AnalysisUtil
			// .toLittleEndian(l)));
			// }
		}
	}
	
	static public void printDiffUsage() {
		System.out.println("Usage: 1. java Diff <from_hashset> <to_hashset>");
		System.out.println("       2. java Diff -a <from_hashset> <to_hashset>");
		System.out.println("       3. java Diff -s <from_hashset> <to_hashset>");
	}
	
	public void addToSet(String from, String to) {
		HashSet<Long> fromSet = AnalysisUtil.initSetFromFile(from),
			toSet = AnalysisUtil.initSetFromFile(to);
		toSet.addAll(fromSet);
		AnalysisUtil.writeSetToFile(to, toSet);
	}
	
	public static void main(String[] argvs) {
//		Getopt g = new Getopt("Diff", argvs, "as");
//		int c;
//		boolean error = false;
//		
//		
//		while ((c = g.getopt()) != -1) {
//			switch (c) {
//				case 'a':
//					append = false;
//					break;
//				case 's':
//					dir4Files = g.getOptarg();
//					if (dir4Files.startsWith("-"))
//						error = true;
//					break;
//				case '?':
//					error = true;
//					System.out.println("parse error for option: -" + (char) g.getOptopt());
//					break;
//				default:
//					break;	
//			}
//		}
//		if (error) {
//			Diff.printDiffUsage();
//			return;
//		}
//		
//		HashMap<Long, Integer> freqTable = ClusteringAnalysis.loadFreqTableFromFile("freqFile");
//		System.out.println("Freq Map Size: " + freqTable.size());
		
		Diff d = new Diff();

		
		String from = null, to = null;
		if (argvs.length == 3) {
			from = argvs[1];
			to = argvs[2];
			if (argvs[0].equals("-a")) {
				d.addToSet(from, to);
				System.out.println("Successfully add " + from + " to " + to + ".");
			} else if(argvs[0].equals("-s")) {
				d.diff(from, to, 1);
			} else {
				Diff.printDiffUsage();
				return;
			}
		} else if (argvs.length == 2) {
			from = argvs[0];
			to = argvs[1];
			d.diff(from, to, 0);
		} else {
			Diff.printDiffUsage();
		}
		return;
	}

}
