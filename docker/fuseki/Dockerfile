FROM stain/jena-fuseki

#Need to install procps to allow the container to run "ps". Otherwise, restarting Fuseki will fail
#RUN apk update && apk install -y --no-install-recommends procps

USER root
RUN apk add procps

# set password for user "admin"
ENV ADMIN_PASSWORD password

# create the connector self description database
#ADD connectorData.ttl /fuseki/configuration/connectorData.ttl
#ADD connectorDataTest.ttl /fuseki/configuration/connectorDataTest.ttl