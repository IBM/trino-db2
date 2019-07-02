# Presto DB2 connector [![Build Status](https://travis-ci.org/IBM/presto-db2.svg?branch=master)](https://travis-ci.org/IBM/presto-db2)

This is a plugin for [Presto](https://prestosql.io/) that allow you to use IBM DB2 Jdbc Connection

## Connection Configuration

Create new properties file inside `etc/catalog` dir:

    connector.name=db2
    connection-url=jdbc:db2://ip:port/database
    connection-user=myuser
    connection-password=mypassword

For a connection with SSL, uses following JDBC URL strings as `connection-url`:

    connection-url=jdbc:db2://ip:port/database:sslConnection=true;

**Notice**: the trailing semi-colon is required. Or it will thrown SQLException `Invalid database URL syntax`.

See official document of DB2 JDBC details from the article [Connecting programmatically with JDBC](https://www.ibm.com/support/knowledgecenter/en/SS6NHC/com.ibm.swg.im.dashdb.doc/connecting/connect_connecting_jdbc_applications.html).

## Building Presto DB2 JDBC Plugin

    mvn clean install
