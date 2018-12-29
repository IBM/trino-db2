# Presto DB2 connector [![Build Status](https://travis-ci.com/IBM/presto-db2.svg?branch=master)](https://travis-ci.com/IBM/presto-db2)

This is a plugin for Presto that allow you to use IBM DB2 Jdbc Connection

## Connection Configuration

Create new properties file inside `etc/catalog` dir:

    connector.name=db2
    connection-url=jdbc:db2://ip:port/database
    connection-user=myuser
    connection-password=mypassword

## Building Presto DB2 JDBC Plugin

    mvn clean install
