#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

void f0();
void f1();
void f2();
void f3();

/** 
 ** This is basically the same as testcase-branch.c with only little difference
 ** but it has funny result.

 ** In the first branch, don't call f1() and just add to it a simple instruction.
 ** Running with parameter 10 or 11 will give a few new hash codes mainly because
 ** it will be different whether or not it calls the library function printf().
 ** This involves some dynamic linkage issues.
 **
 **/

int main(int argc, char **argv) {
	if (argc != 2) {
		printf("Wrong usage!\n");
		exit(-1);
	}
	
	if (argv[1][0] == '0')
		f0();
	else
		// if we only call f1() in this else block, things would happen as expected
		//f1();
		argc++;

	if (argv[1][1] == '0')
		f2();
	else
		f3();

	return 1;
}

int array_0[8] = {0, 9, -1, 8, 3, 5, 7, 2};
int tmp_array[8];

// merge sort
void merge(int *arr, int begin, int mid, int end) {
	int i = begin, j = mid, pos = begin;
	// int *tmp_array = malloc(sizeof(int) * (end - begin + 1);
	int k;
	for (k = begin; k <= end; k++)
		tmp_array[k] = arr[k];
	while (i < mid && j <= end) {
		if (tmp_array[i] <= tmp_array[j])
			arr[pos++] = tmp_array[i++];
		else
			arr[pos++] = tmp_array[j++];
	}
	if (i == mid)
		while (j <= end)
			arr[pos++] = tmp_array[j++];
	else
		while (i < mid)
			arr[pos++] = tmp_array[i++];
}

#define NUM_0 8
#define ARR_0 array_0
void f0() {
	int stride = 1;
	while (stride < NUM_0) {
		int index = 0;
		while (index + stride < NUM_0) {
			int end = index + 2 * stride - 1;
			end = end < NUM_0 ? end : NUM_0 - 1;
			merge(ARR_0, index, index + stride, end);
			index += (2 * stride);
		}
		stride *= 2;
	}
	printf("The merge-sorted array is:\n");
	int k;
	for (k = 0; k < NUM_0; k++)
		printf("%d ", ARR_0[k]);
	printf("\n");
}


// quick sort
int array_1[8] = {1, 33, 2, 11, 23, 9, 8, 7};
#define NUM_1 8
#define ARR_1 array_1
void f1_helper(int *arr, int begin, int end) {
	if (begin >= end)
		return;
	int val = arr[begin], pos = begin;
	int k;
	for (k = begin + 1; k <= end; k++) {
		if (arr[k] < val) {
			arr[pos++] = arr[k];
			arr[k] = arr[pos];
			arr[pos] = val;
		}
	}
	f1_helper(arr, begin, pos - 1);
	f1_helper(arr, pos + 1, end);
}

void f1() {
	f1_helper(ARR_1, 0, NUM_1 - 1);
	printf("The quick-sorted array is:\n");
	int k;
	for (k = 0; k < NUM_1; k++)
		printf("%d ", ARR_1[k]);
	printf("\n");
}

int array_2[8] = {3, 4, 6, 7, 9, 10, 33, 99};
#define ARR_2 array_2
#define NUM_2 8
#define TARGET 2
// binary search
void f2() {
	int low = 0, high = NUM_2 - 1, index = -1;
	while (low <= high) {
		int mid = (low + high) / 2;
		if (ARR_2[mid] == TARGET) {
			index = mid;
			break;
		}
		else if (ARR_2[mid] > TARGET)
			high = mid - 1;
		else
			low = mid + 1;
	}
	if (index == -1)
		printf("The element %d is not in this array!\n", TARGET);
	else
		printf("The element %d is in the index of %d\n", TARGET, index);
}


#define ARR_3 array_1
#define NUM_3 8
// reverse array
void f3() {
	int k, low = 0, high = NUM_3 - 1;
	for (k = 0; k < NUM_3 / 2; k++) {
		int tmp = ARR_3[low];
		ARR_3[low] = ARR_3[high];
		ARR_3[high] = tmp;
		low++;
		high--;
	}
	printf("The reversed array is:\n");
	for (k = 0; k < NUM_3; k++)
		printf("%d ", ARR_3[k]);
	printf("\n");
}

#define ARR_4 array_1












