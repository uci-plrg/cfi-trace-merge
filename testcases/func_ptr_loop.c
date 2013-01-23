#include <stdio.h>

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

int main() {

	fp_t fp = (fp_t) 0;
	int res = 0;

	// add 2 to res and then subtract 1 from res, or increase res by 1
	while (1) {
		fp = &plus;
		res = (*fp)(res, 2);
		fp = &minus;
		res = (*fp)(res, 1);
		if (res == 65535)
			break;
	}

	// print the result twice
	//printf("%d\n", res);
	//printf("%d\n", res);
}




