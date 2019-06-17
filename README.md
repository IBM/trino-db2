# Presto DB2 connector [![Build Status](https://travis-ci.org/IBM/presto-db2.svg?branch=master)](https://travis-ci.org/IBM/presto-db2)

This is a plugin for [Presto](https://prestosql.io/) that allow you to use IBM DB2 Jdbc Connection

## Connection Configuration

Create new properties file inside `etc/catalog` dir:

    connector.name=db2
    connection-url=jdbc:db2://ip:port/database
    connection-user=myuser
    connection-password=mypassword
    connection-ssl=true

## Building Presto DB2 JDBC Plugin

    mvn clean install -DskipTests -Dair.check.skip-all=true
