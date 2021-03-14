# Presto DB2 connector [![Actions Status](https://github.com/IBM/presto-db2/workflows/Java%20CI/badge.svg)](https://github.com/IBM/presto-db2/actions) [![Build Status](https://travis-ci.org/IBM/presto-db2.svg?branch=master)](https://travis-ci.org/IBM/presto-db2)

This is a plugin for [Presto](https://prestosql.io/) that allow you to connect to IBM Db2 database.

Notice that it requires the connected database to be Db2 10 or Db2 LUW 9.7+ or greater versions to meet the precision need of the timestamp data type.

See [DEVELOPMENT](DEVELOPMENT.md) for information on development process.

## Connection Configuration

Create new properties file like `<catalog-name>.properties` inside `etc/catalog` dir:

    connector.name=db2
    connection-url=jdbc:db2://ip:port/database
    connection-user=myuser
    connection-password=mypassword

For a connection with SSL, uses following JDBC URL strings as `connection-url`:

    connection-url=jdbc:db2://ip:port/database:sslConnection=true;

**Notices**:
* the trailing semi-colon is required. Or it will throw SQLException `Invalid database URL syntax`.
* You can use `db2.iam-api-key` to specify API Key instead of user/password if IAM authentication is supported.

See the official document of DB2 JDBC details from the article [Connecting programmatically with JDBC](https://www.ibm.com/support/knowledgecenter/en/SS6NHC/com.ibm.swg.im.dashdb.doc/connecting/connect_connecting_jdbc_applications.html).

## Configuration Properties

| Property Name | Description |
|---------------|-------------|
|`db2.varchar-max-length` | max length of VARCHAR type in a CREATE TABLE or ALTER TABLE command. default is `32672`|
|`db2.iam-api-key` | API Key of IBM Cloud IAM. Use this when choosing IAM authentication instead of user/password |

**Notice**: you may need to customize value of `db2.varchar-max-length` to `32592` when using Db2 warehouse.

## _Extra credentials_ Support

Since release `324`, it starts to support the idea of _extra credentials_ where it allows prestosql client user to provide DB2 username and password as extra credentials that are passed directly to the backend DB2 server when running a Presto query.

1. configure this for the DB2 connector catalog properties file:
```
user-credential-name=db2_user
password-credential-name=db2_password
```
2. passing credentials directly to DB2 server:
```
presto --extra-credential db2_user=user1 --extra-credential db2_password=secret
```

See details from [this answer](https://stackoverflow.com/a/58634432/914967).