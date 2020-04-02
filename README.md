# Deliverables Analyzer

This application runs with:

- Quarkus
- Docker and Docker Compose

## Building with Maven

To build with Maven and run the tests:

```
$ mvn clean install
```

## Creating Docker Images with Docker Compose

To also build the Docker image:

```
mvn -Pdocker clean install
```

This is the equivalent of manually running:

```
$ docker-compose pull
$ docker-compose up --build
$ docker-compose down --rmi --remove-orphans -v
```
