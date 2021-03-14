 # Development

 Developers should follow the [development guidelines](https://github.com/trinodb/trino/blob/81e9233eae31f2f3b425aa63a9daee8a00bc8344/DEVELOPMENT.md)
 from the Trino community.

## Build

    mvn clean install

## Build a container image including this connector

It uses multi-stage build and the prestosql container image from community as the
base image.

    docker build -t "<name>/<tag>" --build-arg BASE="prestosql/presto:347" .

## Testing

So far, it still depends on manual integration testing against an actual Db2
database after coding a feature in Java code.

I'd recommend following this process to iterate:

1. Clone this repo to a local development environment, e.g., IntelliJ IDEA. And
keep code changes in a branch.
1. Run `mvn clean install` or the Maven tool window of the IDE to build this
connector, while addressing errors/problems from build output.
1. Config a separate prestosql server with the built connector by creating a file
named `docker-compose.yml`:
    ```YAML
    # docker-compose.yml
    version: "3.7"

    services:
    presto-coordinator:
        image: prestosql/presto:347
        container_name: presto-coordinator
        volumes:
        - source: ./target/presto-db2-347
        target: /usr/lib/presto/plugin/db2
        type: bind
        - source: ./conf/presto
        target: /etc/presto
        type: bind
        ports:
        - "8080:8080"
    ```
1. Make sure creating a connector config under `./conf/presto/catalog` to connect
to an actual Db2 database. see details from [Connection Configuration](README.md#connection-configuration).
1. Start this local prestosql server by running `docker-compose up -d`
1. Connect to this local prestosql server via CLI to perform queries while
capturing server output from container logs by running command `docker logs presto-coordinator`.
1. If changing Java code, delete this local prestosql server by running command
`docker-compose down` then start from step 2.