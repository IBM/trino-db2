 # Development

 Developers should follow the [development guidelines](https://github.com/trinodb/trino/blob/81e9233eae31f2f3b425aa63a9daee8a00bc8344/DEVELOPMENT.md)
 from the Trino community.

## Build

    mvn clean install

## Release
First update the `main` branch of this repo via PR process. Then, go to https://github.com/IBM/trino-db2/releases to draft your release. Configure the release to create a new branch named after the Trino version (e.g. 372). Before publishing the release, build the plugin locally with `mvn clean install`, and upload the resulting archive `target/trino-db2-[version].zip` to the release binaries. Then, you may click "publish release".

## Build a container image including this connector

It uses multi-stage build and the trinodb container image from community as the
base image.

    docker build -t "<name>/<tag>" --build-arg BASE="trinodb/trino:<trino_verson_from_pom>" .

## Testing

So far, it still depends on manual integration testing against an actual Db2
database after coding a feature in Java code.

I'd recommend following this process to iterate:

1. Clone this repo to a local development environment, e.g., IntelliJ IDEA. And
keep code changes in a branch.
1. Run `mvn clean install` or the Maven tool window of the IDE to build this
connector, while addressing errors/problems from build output.
1. Config a separate trinodb server with the built connector by creating a file
named `docker-compose.yml`:
    ```YAML
    # docker-compose.yml
    version: "3.7"

    services:
    trino-coordinator:
        image: trinodb/trino:<trino_verson_from_pom>
        container_name: trino-coordinator
        volumes:
        - source: ./target/trino-db2-<trino_verson_from_pom>
        target: /usr/lib/trino/plugin/db2
        type: bind
        - source: ./conf/trino
        target: /etc/trino
        type: bind
        ports:
        - "8080:8080"
    ```
1. Make sure creating a connector config under `./conf/trino/catalog` to connect
to an actual Db2 database. see details from [Connection Configuration](README.md#connection-configuration).
1. Start this local trinodb server by running `docker-compose up -d`
1. Connect to this local trinodb server via CLI to perform queries while
capturing server output from container logs by running command `docker logs trino-coordinator`.
1. If changing Java code, delete this local trinodb server by running command
`docker-compose down` then start from step 2.
