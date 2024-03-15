FROM ubuntu:22.04
RUN apt update && apt install --no-install-recommends -y vim && apt install --no-install-recommends -y fio && apt install  --no-install-recommends -y bc && rm -rf /var/lib/apt/lists/*; 
RUN mkdir /benchmark
COPY run-benchmarks.sh test-list.txt /benchmark
RUN chmod +x /benchmark/run-benchmarks.sh
CMD /benchmark/run-benchmarks.sh < /benchmark/test-list.txt