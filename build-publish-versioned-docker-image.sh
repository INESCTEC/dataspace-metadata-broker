#!/bin/bash

./mvnw clean package -DskipTests

docker login docker-registry.inesctec.pt

# remove local docker images (minor & patch version)
docker image remove docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0
docker image remove docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0.0

# build local docker image (minor version)
docker image build --no-cache --provenance=false --pull --tag docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0 .

# send local docker image to repository (minor version)
docker push docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0

# tag local docker image with patch version and send it to repository
docker image tag docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0 docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0.0
docker push docker-registry.inesctec.pt/ids/metadata-broker-inesctec:3.0.0
