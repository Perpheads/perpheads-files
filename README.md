# Perpheads Files

A very basic [ShareX](https://getsharex.com/) target for uploading images/files that also
supports sharing files in a P2P fashion similar to [JustBeamIt](https://justbeamit.com/).

Perpheads Files is written in Kotlin and Typescript, using [Quarkus](https://quarkus.io/) as the
web server and Restful API, and Typescript for React as a single page application.

Users are authenticated without password using Steam authentication.

[![build](https://github.com/Perpheads/perpheads-files/actions/workflows/build.yml/badge.svg)](https://github.com/Perpheads/perpheads-files/actions/workflows/build.yml)

## How to deploy

The application is built as a docker container.
You can find prebuilt containers in the docker registry
of this repository.

The following environment variables can be set to configure the
settings of the docker container.

Please note that this application uses http rather than https and is expected
to be run behind a reverse proxy that does TLS termination (For example nginx).

The files are stored locally in the docker container (by default in the ``files`` sub-directory).
If you want to persist the uploaded files, create a volume at that path.

You can view sample [docker-compose.yml](docker-compose.sample.yml) that easily bootstraps all of the
services required for running Perpheads Files in production.

### Environment Variables

These are the environment variables that you will either need to pass or define in a local `.env` file.

#### Database Settings

| Environment Variable        | Description                                                                            |
|-----------------------------|----------------------------------------------------------------------------------------|
| QUARKUS_DATASOURCE_JDBC_URL | JDBC URL to connect to the database, for example `jdbc:postgresql://localhost/phfiles` |
| QUARKUS_DATASOURCE_USERNAME | Username to connect to the database                                                    |
| QUARKUS_DATASOURCE_PASSWORD | Password of the database user                                                          |

#### S3 settings

| Environment Variable | Description                                             |
|----------------------|---------------------------------------------------------|
| S3_BUCKET_ENDPOINT   | The endpoint of the S3 bucket files will be uploaded to |
| S3_BUCKET_ACCESS_KEY | The access key of the S3 credentials                    |
| S3_BUCKET_SECRET_KEY | The secret key of the S3 credentials                    |
| S3_BUCKET_NAME       | The name of the S3 bucket                               |



## Development

### Requirements

- IntelliJ IDE or Gradle
- PostgreSQL as a database
- JDK 21+

This project uses [Jooq](https://www.jooq.org/) in combination
with [Liquibase](https://www.liquibase.com/) to migrate an existing
database and automatically generate
a typesafe Java API for accessing the database.

This implies that for syntax highlighting to work properly in the
DAOs, you will have to first run the application successfully using gradle.


### Gradle configuration

You need two configurations to run the serer and the client. The client configuration has a continuous build feature that
can automatically detect changes and rebuilds the project similar to how node works.

#### Server

In IntelliJ, you can either use the existing Quarkus plugin to run the application, or simply run the ``:quarkusDev`` Gradle task. 

If all environment variables are filled in correctly, the database should automatically be migrated upon running
the configuration. Jooq should generate all of its files and the server should start
and respond at port 8080.


### Creating an initial admin user

Unfortunately, there is no easy way to create the first admin user yet and
it has to be done manually through the database by inserting a record into the `user` table.

Authentication is done via Steam, 

### Use with ShareX

After logging in, press the three dots in the top right and select
```Get API Key```. From here you can copy the ShareX config
that can be used in ShareX's custom uploader settings.
Don't forget to also change the upload targets to
your custom uploader, after importing it.