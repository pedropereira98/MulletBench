FROM gcc:latest
RUN apt-get update
RUN apt-get install -y vim
COPY dhrystone-modified-meruje /usr/src/dhrystone-modified-meruje
COPY whetstone /usr/src/whetstone
COPY run-benchmarks.sh /usr/src
WORKDIR /usr/src
CMD ["./run-benchmarks.sh"]