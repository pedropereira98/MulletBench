CC=gcc
LDFLAGS=-lm
CFLAGS=-g

all: optimize-docker-cpu

optimize-docker-cpu: src/optimize-docker-cpu.c
	$(CC) $(CFLAGS) $? -o $@ $(LDFLAGS)

clean:
	rm optimize-docker-cpu