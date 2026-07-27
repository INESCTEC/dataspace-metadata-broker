# Repository Guide

## Project Overview

This repository contains INESC TEC's implementation of the International Data
Spaces (IDS) Metadata Broker. The broker acts as a central registry and
discovery service for data providers and consumers in an IDS ecosystem.

IDS connectors publish metadata about themselves and their resources to the
broker. Other participants can retrieve that metadata in RDF formats or query
it with SPARQL.

The project is currently version `2.2.1` and is licensed under the Apache
License 2.0.

## Main Capabilities

- Register IDS connectors and their resource catalogs.
- Update or deactivate connectors and individual resources through IDS
  messages.
- Return the catalog of active connectors.
- Return the broker's IDS self-description.
- Execute SPARQL `SELECT`, `DESCRIBE`, `ASK`, and `CONSTRUCT` queries.
- Serialize RDF as JSON-LD, Turtle, RDF/XML, or N-Triples.
- Process connector updates, resource updates, unavailability notifications,
  description requests, and query messages.
- Validate IDS Dynamic Attribute Tokens (DATs) through DAPS.
- Expose health, OpenAPI/Swagger, IDS multipart messaging, and administrative
  endpoints.

## Technology Stack

- Java 21
- Maven and Maven Wrapper
- Spring Boot 2.7
- Spring MVC and embedded Tomcat
- Spring Security with stateless HTTP Basic authentication
- Apache Jena for RDF models and SPARQL
- Apache Jena Fuseki for persistent RDF graph storage
- IDS Messaging Services
- DAPS token validation
- Optional IDSCP2 support
- Springdoc OpenAPI/Swagger
- JUnit, Mockito, AssertJ, and Spring test utilities
- Docker and Docker Compose

## Code Organization

The application entry point is:

`src/main/java/pt/inesctec/ids/broker/BrokerApplication.java`

Important packages:

- `controller`: REST endpoints, IDS multipart ingress, health checks, and
  administrative operations.
- `messaging`: IDS protocol message handlers.
- `service`: broker catalog, RDF graph, description, query, and controller
  orchestration logic.
- `config`: Fuseki, IDS, and response configuration.
- `util`: connector, graph-name, query, Fuseki, message, and resource helpers.
- `converter`: JSON, multipart, RDF/Fuseki, and XML calendar conversion.
- `builder`: response header, multipart message, and RDF triple builders.
- `dto`: internal data-transfer objects.
- `exceptions`: domain and request-validation exceptions.

Key services:

- `BrokerService`: registers and updates connectors/resources, marks connectors
  unavailable, and executes SPARQL queries.
- `JenaService`: reads, writes, updates, and queries RDF graphs in Fuseki.
- `DescriptionService`: constructs descriptions of the catalog, connectors,
  and resources with configurable traversal depth and RDF output format.
- `AdminGraphService`: maintains the administrative graph that records active
  connectors.
- `ModelService`: finds, converts, traverses, and removes connector graphs.
- `ControllerService`: validates and dispatches IDS messages.

## Storage Model

Connector metadata is stored as named RDF graphs in an external Fuseki server.
An administrative graph tracks which connector graphs are active. Do not assume
that the presence of a graph alone means that its connector is currently
available.

The bundled Fuseki development environment is located under `docker/fuseki`.
By default, the application expects:

- Fuseki server: `http://localhost:3032`
- Dataset: `connectorData`
- Broker application port: `8082`

## HTTP Interfaces

The primary routes are:

- `GET /`: return the broker self-description.
- `POST /api/ids/data`: process IDS multipart messages.
- `GET /connectors` and `GET /connectors/**`: retrieve the active connector
  catalog or a requested catalog element.
- `POST /connectors`: register a connector.
- `GET /connectors/ui`: Swagger-oriented catalog lookup.
- `GET /health`: return application health.
- `/api-docs`: Swagger UI.
- `/v3/api/docs`: OpenAPI document.

Public routes are configured in `SecurityConfig.java`. Other routes require
stateless HTTP Basic authentication.

## Configuration

The main configuration file is:

`src/main/resources/application.properties`

It contains settings for:

- HTTP port, TLS, and multipart request limits
- Fuseki connection and dataset
- Broker catalog URI
- DAPS endpoints and DAT validation
- IDS messaging, SHACL validation, and policy negotiation
- IDSCP2
- Spring Security credentials
- OpenAPI/Swagger
- HTTP timeouts and logging

Development values in this file include placeholder/default credentials and
must not be treated as production-safe values.

The following local certificate files are intentionally not committed and must
be recreated or supplied for environments that require them:

- `src/main/resources/conf/vcese19.inesctec.pt.p12`
- `src/main/resources/conf/truststore.p12`

## Build and Test

Start the local Fuseki service before integration-style tests:

```powershell
docker compose -f docker/fuseki/docker-compose.yml up
```

Run the test suite on Windows:

```powershell
.\mvnw.cmd clean test
```

Package the application:

```powershell
.\mvnw.cmd clean package
```

The test tree mirrors the production packages and covers controllers, message
handlers, services, converters, Fuseki integration, SPARQL queries, DTOs, and
utilities.

## Deploy

Use these two scrips to build and deploy the correspondent docker image:

```powershell
build-publish-snapshot-docker-image.sh
build-publish-versioned-docker-image.sh
```

## Development Guidance

- Preserve IDS protocol semantics when translating between HTTP requests,
  message headers, response messages, and HTTP status codes.
- Validate DATs before accepting connector metadata.
- Keep connector graph updates and the administrative active/inactive state
  consistent.
- Treat RDF output format and description traversal depth as part of the public
  API behavior.
- Add or update tests in the matching package whenever controller, handler,
  storage, or query behavior changes.
- Avoid committing keystores, truststores, tokens, passwords, or
  environment-specific endpoints.
- Check `application-test.properties` when behavior differs under tests.

