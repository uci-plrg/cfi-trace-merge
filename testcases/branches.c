#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

/**
 ** In this testcase, it takes one input parameter in the format of a 4-bit
 ** binary number, e.g. "1001", indicating the choices over the 4 branches.
 ** 0 means it takes a left branch while 1 means the right. In each branch,
 ** it simply calls a different function. For example, if you run it like
 ** <ProgName> 1110, it will take the right option for the first 3 branches
 ** and the left for the 4th one.
 **
 **/

void f0();
void f1();
void f2();
void f3();
void f4();
void f5();
void f6();
void f7();

int main(int argc, char **argv) {
	if (argc != 2) {
		printf("Wrong usage!\n");
		exit(-1);
	}
	
	if (argv[1][0] == '0')
		f0();
	else
		f1();

	if (argv[1][1] == '0')
		f2();
	else
		f3();


	if (argv[1][2] == '0')
		f4();
	else
		f5();


	if (argv[1][3] == '0')
		f6();
	else
		f7();

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
void f4() {
   int n = 10, i = 3, count, c;
 
   printf("Enter the number of prime numbers required\n");
   //scanf("%d",&n);
 
   if ( n >= 1 )
   {
      printf("First %d prime numbers are :\n",n);
      printf("2 ");
   }
 
   for ( count = 2 ; count <= n ;  )
   {
      for ( c = 2 ; c <= i - 1 ; c++ )
      {
         if ( i%c == 0 )
            break;
      }
      if ( c == i )
      {
         printf("%d ",i);
         count++;
      }
      i++;
   }
   printf("\n");
 
}


#define ARR_5 array_1
void f5() {
   int n = 10, first = 0, second = 1, next, c;
 
   printf("First %d terms of Fibonacci series are :-\n",n);
 
   for ( c = 0 ; c < n ; c++ )
   {
      if ( c <= 1 )
         next = c;
      else
      {
         next = first + second;
         first = second;
         second = next;
      }
      printf("%d ",next);
   }
   printf("\n");
}


char *str_6 = "abcdefghijklmnopqrstuvwxyz";
#define ARR_6 array_1
#define STRING str_6
#define BEGIN 3
#define LENGTH 10
char *substring(char *string, int position, int length) 
{
   char *pointer;
   int c;
 
   pointer = malloc(length+1);
 
   if (pointer == NULL)
   {
      printf("Unable to allocate memory.\n");
      exit(EXIT_FAILURE);
   }
 
   for (c = 0 ; c < position -1 ; c++) 
      string++; 
 
   for (c = 0 ; c < length ; c++)
   {
      *(pointer+c) = *string;      
      string++;   
   }
 
   *(pointer+c) = '\0';
 
   return pointer;
}

void f6() {
   char *substr = substring(STRING, BEGIN, LENGTH);
   printf("%s\n", substr);
}

char *str7_1 = "helper";
char *str7_2 = "hallena";
#define STR_7_1 str7_1
#define STR_7_2 str7_2

int compare(char a[], char b[])
{
   int c = 0;
 
   while( a[c] == b[c] )
   {
      if( a[c] == '\0' || b[c] == '\0' )
         break;
      c++;
   }
   if( a[c] == '\0' && b[c] == '\0' )
      return 0;
   else
      return -1;
}

void f7() {
   if (compare(STR_7_1, STR_7_2) > 0)
      printf("%s is greater than %s\n", STR_7_1, STR_7_2);
   else if (compare(STR_7_1, STR_7_2) < 0)
      printf("%s is greater than %s\n", STR_7_2, STR_7_1);
   else
      printf("%s is equal to %s\n", STR_7_1, STR_7_2);
}
