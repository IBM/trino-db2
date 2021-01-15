ARG BASE
FROM docker.io/library/maven:3.6.3-openjdk-11 AS builder
WORKDIR /root/presto-db2
COPY . /root/presto-db2
ENV MAVEN_FAST_INSTALL="-DskipTests -Dair.check.skip-all=true -Dmaven.javadoc.skip=true -B -q -T C1"
RUN mvn install $MAVEN_FAST_INSTALL

FROM $BASE
COPY --from=builder --chown=presto:presto /root/presto-db2/target/presto-db2-*/* /usr/lib/presto/plugin/db2/
