FROM ubuntu:22.04
RUN apt-get update && apt-get install --no-install-recommends -y vim && apt-get install --no-install-recommends -y fio && rm -rf /var/lib/apt/lists/*;
RUN mkdir /benchmark
COPY run-benchmarks.sh test-list.txt /benchmark
RUN chmod +x /benchmark/run-benchmarks.sh
CMD /benchmark/run-benchmarks.sh < /benchmark/test-list.txt