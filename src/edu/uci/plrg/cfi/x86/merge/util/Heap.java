package edu.uci.plrg.cfi.x86.merge.util;

import java.util.ArrayList;
import java.util.Comparator;

public class Heap<Type> {
	// The comparator can determine whether this is a MaxHeap or MinHeap
	private Comparator<Type> comparator;

	// The first element (index as 0) is left to be a sentinel element
	// Actual elements start from 1;
	private ArrayList<Type> elems;

	public Heap() {
		elems = new ArrayList<Type>();
		// act as a placeholder only
		elems.add(null);
	}

	public Heap(Comparator<Type> comparator) {
		this.comparator = comparator;
		elems = new ArrayList<Type>();
		elems.add(null);
	}

	public void setComparator(Comparator<Type> comparator) {
		this.comparator = comparator;
	}

	public int size() {
		return elems.size() - 1;
	}

	public Type getMaxElem() {
		if (size() == 0)
			return null;
		else
			return elems.get(1);
	}

	public void insertElem(Type newElem) {
		elems.add(newElem);
		int index = size(), parentIndex;
		while (index > 1) {
			parentIndex = index / 2;
			if (comparator.compare(elems.get(parentIndex), newElem) < 0) {
				elems.set(index, elems.get(parentIndex));
				index = parentIndex;
			} else {
				break;
			}
		}
		elems.set(index, newElem);
	}

	public Type removeMaxElem() {
		if (size() == 0)
			return null;
		Type t = elems.get(size()), maxElem = elems.get(1);
		elems.set(1, t);
		elems.remove(size());
		siftDown(1);
		return maxElem;
	}

	public boolean swapMaxElemWith(Type newElem) {
		if (size() == 0)
			return false;
		elems.set(1, newElem);
		siftDown(1);
		return true;
	}

	private void siftDown(int index) {
		// first check if size == 0
		if (size() == 0)
			return;

		int leftSonIndex, rightSonIndex;
		int maxSonIndex = -1;
		Type maxSon = null;
		// first store the target element in index 0
		elems.set(0, elems.get(index));

		while (true) {
			leftSonIndex = 2 * index;
			rightSonIndex = 2 * index + 1;
			if (leftSonIndex <= size()) {
				maxSon = elems.get(leftSonIndex);
				maxSonIndex = leftSonIndex;
				if (rightSonIndex <= size()
						&& comparator.compare(elems.get(leftSonIndex), elems.get(rightSonIndex)) < 0) {
					maxSon = elems.get(rightSonIndex);
					maxSonIndex = rightSonIndex;
				}
			}
			if (leftSonIndex > size() || comparator.compare(maxSon, elems.get(0)) < 0)
				break;
			else if (leftSonIndex <= size()) {
				elems.set(index, maxSon);
				index = maxSonIndex;
			}
		}
		elems.set(index, elems.get(0));
	}

	public static <Type> void sortByHeap(Type[] arr, Comparator<Type> comparator) {
		Heap<Type> heap = buildHeapFromArray(arr, comparator);
		int index = 0;
		while (heap.size() > 0) {
			arr[index++] = heap.removeMaxElem();
		}
	}

	public static <Type> Heap<Type> buildHeapFromArray(Type[] arr, Comparator<Type> comparator) {
		Heap<Type> heap = new Heap<Type>();
		heap.setComparator(comparator);
		if (arr == null || arr.length == 0)
			return heap;
		for (int i = 0; i < arr.length; i++) {
			heap.insertElem(arr[i]);
		}
		return heap;
	}

	public static class MyComparator implements Comparator<Integer> {

		public int compare(Integer o1, Integer o2) {
			return o1 - o2;
		}
	}

	public static void main(String[] argvs) {
		// MyComparator comparator = new MyComparator();
		// Heap<Integer> maxHeap;
		// Integer arr[] = {5, 3, 1, 6, 3, 8};
		// Heap.sortByHeap(arr, comparator);
		// for (int i = 0; i < arr.length; i++) {
		// System.out.print(arr[i] + " ");
		// }
		// Log.log();
	}

}
