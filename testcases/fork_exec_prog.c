#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>

int fork_process(char *prog)
{
	pid_t pid = fork();
	if (pid == 0) {    // child process
		if (execl(prog, "--version", (char*) 0) == -1) {
			printf("%s\n", "An error occured when executing execl ls");
			exit(-1);
		}
	} else if (pid < 0) {
		printf("%s\n", "An error occured when forking another process");
		exit(-1);
	}
	return pid;
}

int main(int argc, char **argv)
{
	fork_process("/bin/ls");
	fork_process("/bin/grep");
	fork_process("/usr/bin/find");
	while (wait() > 0) {
		;
	}
	return 1;
}
