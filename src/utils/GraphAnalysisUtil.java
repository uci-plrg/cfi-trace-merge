package utils;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import analysis.graph.ExecutionGraph;
import analysis.graph.ExecutionGraph.Node;


public class GraphAnalysisUtil {
	public static HashMap<Long, Node> readGraphLookup(String fileName) {
		HashMap<Long, Node> lookupTable = new HashMap<Long, Node>();
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;
		try {
			fileIn = new FileInputStream(fileName);
			dataIn = new DataInputStream(fileIn);
			long tag = 0, hash = 0;
			while (true) {
				tag = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());
				hash = AnalysisUtil.reverseForLittleEndian(dataIn.readLong());
				if (lookupTable.containsKey(tag)) {
					System.out.println("Something's wrong??");
					return null;
				}
				lookupTable.put(tag, new Node(tag, hash));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (dataIn != null) {
			try {
				dataIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return lookupTable;
	}
	
	/**
	 * 
	 * @param fileName
	 * @return an ArrayList of tag seen in order (order in the file)
	 */
	public static ArrayList<Long> readGraph(String fileName, ExecutionGraph graph) {
		File file = new File(fileName);
		// V <= E / 2 + 1
		int capacity = (int) file.length() / 16 / 2 + 1;
		ArrayList<Long> tags =null;
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;
		try {
			fileIn = new FileInputStream(file);
			dataIn = new DataInputStream(fileIn);
			while (true) {
				tags = new ArrayList<Long>(capacity);
				long tag1 = dataIn.readLong(),
					tag2 = dataIn.readLong();
//				if (graph)
//				tags.add(tag);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (dataIn != null) {
			try {
				dataIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		// since V will never change once the graph is created
		tags.trimToSize();
		return tags;
	}
	
	// get the lower 6 byte of the tag, which is a long integer
	public static long getTagEffectiveValue(long tag) {
		return tag << 16;
	}
}
