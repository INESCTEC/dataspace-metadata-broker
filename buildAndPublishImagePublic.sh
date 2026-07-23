#!/bin/bash

docker login docker-registry.inesctec.pt

dataspace-connector
echo Removing docker images
docker image remove docker-registry.inesctec.pt/ids/dataspace-app-4edi/metadata-broker:latest
docker image remove docker-registry.inesctec.pt/ids/dataspace-app-4edi/metadata-broker:confacts

docker image build --tag docker-registry.inesctec.pt/ids/dataspace-app-4edi/metadata-broker .

docker image tag docker-registry.inesctec.pt/ids/dataspace-app-4edi/metadata-broker docker-registry.inesctec.pt/ids/dataspace-app-4edi/metadata-broker:confacts

docker push --all-tags docker-registry.inesctec.pt/ids/dataspace-app-4edi/metadata-broker

