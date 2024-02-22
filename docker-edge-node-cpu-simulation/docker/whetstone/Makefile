CC=gcc
LDFLAGS=-lm

all: whetstone whetstoneO

whetstone: whetstone.c
	$(CC) whetstone.c -o whetstone $(LDFLAGS)
whetstoneO: whetstone.c
	$(CC) -O whetstone.c -o whetstoneO $(LDFLAGS)

clean:
	rm whetstone