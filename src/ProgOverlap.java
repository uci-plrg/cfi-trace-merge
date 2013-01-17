import java.io.*;
import java.util.*;
import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProgOverlap {

	public HashMap<Vector<Integer>, HashSet<Long>> setMap = new HashMap<Vector<Integer>, HashSet<Long>>();

	public HashSet<Long>[] hashes;

	public HashSet<Long> union;
	public int progNum = 0;

	public String[] progNames;
	public String[] hashsetFilenames;
	
	
	private static String execFileDir = "/home/peizhaoo/crowd-safe-dynamorio-launcher/crowd-safe-dynamorio-launcher/stats/";
	private static String execFile = execFileDir + "tar.hashlog.2013-01-15.19-08-35.dat";
	
	public ProgOverlap(int length) {
		progNames = new String[length];
		hashsetFilenames = new String[length];
		hashes = (HashSet<Long>[]) new HashSet[length];	
	}
	

	public static void main(String[] argvs) {
		
		ProgOverlap po = new ProgOverlap(argvs.length);
		
		for(int i = 0; i < argvs.length; i++) {
			File f = new File(argvs[i]);
	    	po.progNames[i] = AnalysisUtil.getProgName(f.getName());
	    	po.hashsetFilenames[i] = argvs[i] + "/total_hashes.dat";
		}
		
		//File outputDir = new File(argvs[argvs.length - 1]);
		po.initSetMap(po.hashsetFilenames);
		//po.outputOverlapInfo();
		
		HashSet<Long> runSet = AnalysisUtil.initSetFromFile(ProgOverlap.execFile);
		po.classifyProg(runSet);
	}
	
	public void outputOverlapInfo() {
		System.out.println("total hashes of each program : ");
		for (int i = 0; i < this.hashes.length; i++) {
			System.out.println(this.progNames[i] + " : "  + this.hashes[i].size());
		}
		
		System.out.println();
		System.out.println("total hashes union : " + union.size());
		for (Vector<Integer> vi : setMap.keySet()) {
			for (int i = 0; i < vi.size(); i++) {
				if (i != vi.size() - 1)
					System.out.print(progNames[vi.get(i)] + " & ");
				else
					System.out.print(progNames[vi.get(i)] + " : ");
			}
			System.out.println(setMap.get(vi).size());
		}
		
	}

	public HashMap<Vector<Integer>, Integer> classifyProg(HashSet<Long> set) {
		
		System.out.println("Hashes for this run: " + set.size());
		
		HashMap<Vector<Integer>, Integer> distributionMap = new HashMap<Vector<Integer>, Integer>();
		
		Vector<Long> newHash = new Vector<Long>();
		for(Long l : set) {
			
			int newFlag = 1;
			for(Vector<Integer> iv : this.setMap.keySet()) {
				if(this.setMap.get(iv).contains(l)) {
					if (!distributionMap.containsKey(iv))
						distributionMap.put(iv, 0);
					distributionMap.put(iv, distributionMap.get(iv) + 1);
					newFlag = 0;
				}
			}
			
			if(newFlag == 1) {
				Vector<Integer> vi = new Vector<Integer>();
				if (!distributionMap.containsKey(vi))
					distributionMap.put(vi, 0);
				distributionMap.put(vi, distributionMap.get(vi) + 1);
				newHash.add(l);
			}
		}
		
		for (Vector<Integer> vi : distributionMap.keySet()) {
			if (vi.size() == 0) {
				System.out.println("New hashes found : " + distributionMap.get(vi));
				continue;
			}
			for (int i = 0; i < vi.size(); i++) {
				if (i != vi.size() - 1)
					System.out.print(progNames[vi.get(i)] + " & ");
				else
					System.out.print(progNames[vi.get(i)] + " : ");
			}
			System.out.println(distributionMap.get(vi));
		}
		
		return distributionMap;
	}

    public void initSetMap(String[] fileNames) {

		progNum = fileNames.length;
		
		union = new HashSet<Long>();

		for(int i=0;i<fileNames.length;i++) {
		    hashes[i]=AnalysisUtil.initSetFromFile(fileNames[i]);
		    union.addAll(hashes[i]);
		}
		for(Long l: union) {
		    Vector<Integer> intvector=new Vector<Integer>();

		    for(int i = 0 ; i < fileNames.length; i++) {
				if (hashes[i].contains(l))
				    intvector.add(i);
		    }
		    if (!setMap.containsKey(intvector)) {
				setMap.put(intvector, new HashSet<Long>());
		    }
		    setMap.get(intvector).add(l);
		}
   	 }
    
}
