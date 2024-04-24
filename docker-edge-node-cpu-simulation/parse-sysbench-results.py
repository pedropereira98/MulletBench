import sys
import re
import os

threadRegex = re.compile(r'(Number of threads: )(\d+)')
primeRegex = re.compile(r'(Prime numbers limit: )(\d+)')
eventsPerSecondRegex = re.compile(r'\s+(events per second:\s+)(\d+)')

folder = os.listdir(sys.argv[1])

#Print CSV header
print("System;Threads;MaxPrimes;CPUEventPerSecond")

for filename in folder:
    if 'sysbench' in filename:
        #print('File: ' + filename)
        with open(sys.argv[1] + '/' + filename, 'r') as file:
            for line in file.readlines():
                if line.startswith('Number of threads: '):
                    mo = threadRegex.search(line)
                    threads = mo.group(2)
                elif line.startswith('Prime numbers limit:'):
                    mo = primeRegex.search(line)
                    numPrimes = mo.group(2)
                elif line.startswith('    events per second:'):
                    mo = eventsPerSecondRegex.search(line)
                    try:
                        eventsPerSecond = mo.group(2)
                    except:
                        print(line)
                    print(filename + ';' + threads + ';' + numPrimes + ';' + eventsPerSecond )
                    eventsPerSecond = '0'
                    numPrimes = '0'
                    threads = '0'
                    


