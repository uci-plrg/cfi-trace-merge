#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>

int main(int argc, char **argv)
{
	pid_t pid = fork();
	if (pid == 0) {    // child process
		if (execl("/bin/ls", "-l", "/", (char*) 0) == -1) {
			printf("%s\n", "An error occured when executing execl ls");
			exit(-1);
		}
	} else if (pid < 0) {
		printf("%s\n", "An error occured when forking another process");
		exit(-1);
	} else {    // parent process
		/*
		if (execl("/usr/bin/find", "--version", (char*) 0) == -1) {
			printf("%s\n", "An error occured when executing execl find");
			exit(-1);
		}
		*/
		int status;
		wait(&status);
		printf("%s\n", "child process finishes!");
	}
	return 1;
}
