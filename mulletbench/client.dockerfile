FROM maven:3.8-openjdk-18-slim as build

ARG client_module=mulletbench-client
ARG orchestrator_module=mulletbench-orchestrator
ARG common_module=mulletbench-common
ARG version=0.1

COPY pom.xml /home/build/
COPY ${common_module}/src /home/build/${common_module}/src
COPY ${common_module}/pom.xml /home/build/${common_module}/pom.xml
COPY ${client_module}/src /home/build/${client_module}/src
COPY ${client_module}/pom.xml /home/build/${client_module}/pom.xml
COPY ${orchestrator_module}/src /home/build/${orchestrator_module}/src
COPY ${orchestrator_module}/pom.xml /home/build/${orchestrator_module}/pom.xml
WORKDIR /home/build
RUN mvn clean package -pl ${client_module} -am -DskipTests

FROM eclipse-temurin:18-jre-alpine

ARG client_module=mulletbench-client
ARG orchestrator_module=mulletbench-orchestrator
ARG common_module=mulletbench-common
ARG version=0.1

ENV env_client_module=${client_module}
ENV env_orchestrator_module=${orchestrator_module}
ENV env_common_module=${common_module}
ENV env_version=${version}

COPY --from=build /home/build/${client_module}/target/${client_module}-${version}.jar /home/app/${client_module}-${version}.jar

ENTRYPOINT java -Xmx${MAX_MEMORY} -Djava.util.concurrent.ForkJoinPool.common.parallelism=${FORK_JOIN_POOL_PARALLELISM} -jar "/home/app/${env_client_module}-${env_version}.jar" -c ${CONFIG_PATH}