#include <stdio.h>

// Super simple program to test the correctness of indirect branch using
// function pointers.

typedef int (*fp_t)(int, int);

int plus(int a, int b)
{
	return a + b;
}

int minus(int a, int b)
{
	return a - b;
}

fp_t select_func(int operator)
{
	if (operator > 0)
		return &plus;
	else
		return &minus;
}

#define OPERATOR 1
int main(int argc, char **argv) 
{
	fp_t fp = select_func(argc - 1);
	argc = (*fp)(2, 1);
	printf("%d\n", argc);
}
