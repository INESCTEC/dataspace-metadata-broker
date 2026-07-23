#!/bin/bash

./mvnw clean package -DskipTests

docker login docker-registry.inesctec.pt

# remove local docker image
docker image remove docker-registry.inesctec.pt/ids/metadata-broker-inesctec:2.2.SNAPSHOT

# build local docker image
docker image build --tag docker-registry.inesctec.pt/ids/metadata-broker-inesctec:2.2.SNAPSHOT .

# send local docker image to repository
docker push docker-registry.inesctec.pt/ids/metadata-broker-inesctec:2.2.SNAPSHOT
