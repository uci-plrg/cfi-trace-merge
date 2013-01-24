#include <stdio.h>
#include <fcntl.h>

void my_func() {
	char *filename = "bad.txt";
	int fd;
	printf("%s\n", filename);
	
	
	char *buf = "#!/bin/bash\n#rm -i *\n";
					    
	fd = open(filename, O_WRONLY | O_TRUNC | O_CREAT, 0555);
	write(fd, buf, 22);
}

int inc(int a) {
	return a + 16;
}

void main() {
	// copy "bad.txt" to the current stack)
	__asm__ ( "sub $0x8,%rsp\n\t");
	__asm__ ("movl $0x2e646162,(%rsp)\n\t");
	__asm__ ("movl $0x00747874,0x4(%rsp)\n\t");
	
	// call open function
	__asm__ ( "movl $0x16d,%edx\n\t");
	__asm__ ( "movl $0x241,%esi\n\t");
	__asm__ ( "movq %rsp,%rdi\n\t");
	__asm__ ( "movl $0x0,%eax\n\t");

	__asm__ ( "callq 0x400490\n\t");  

	// copy "#!/bin/bash\n#rm -i *\n" to the current stack)
	__asm__ ( "sub $0x18,%rsp\n\t");
	__asm__ ("movl $0x622f2123,(%rsp)\n\t");    
	__asm__ ("movl $0x622f6e69,0x4(%rsp)\n\t");    
	__asm__ ("movl $0x0a687361,0x8(%rsp)\n\t");    
	__asm__ ("movl $0x206d7223,0xc(%rsp)\n\t");    
	__asm__ ("movl $0x2a20692d,0x10(%rsp)\n\t");    
	__asm__ ("movl $0x0000000a,0x14(%rsp)\n\t");
	
	// call write
	__asm__ ( "movl $0x16,%edx\n\t");
	__asm__ ( "movq %rsp,%rsi\n\t");
	__asm__ ( "movl %eax,%edi\n\t");
	__asm__ ( "movl $0x0,%eax\n\t");

	__asm__ ( "callq 0x400470\n\t");

	// restore %rsp
	__asm__ ( "add $0x18,%rsp\n\t");
	__asm__ ( "add $0x8,%rsp\n\t");
	
}

