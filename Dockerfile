# The first stage of the Dockerfile uses a Maven image to build the persistence
# plugin implementing the API specified by Apache Chemistry OpenCMIS
FROM maven:3.9.9-eclipse-temurin-8 AS build
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
ADD pom.xml /usr/src/app
ADD src/ /usr/src/app/src
RUN ["mvn", "-e", "clean", "install"]

# The second stage uses a lightweight OpenJDK image where we'll deploy
# Tomcat and the plugin compiled in the first stage, and configure everything
FROM openjdk:8-jre-alpine

# Declare the listening port
EXPOSE 8080

# Download and deploy the validated Tomcat version
ENV TOMCAT_VERSION_MAJOR=9
ENV TOMCAT_VERSION_FULL=9.0.89
RUN set -x \
  && apk add --no-cache su-exec \
  && apk add --update curl unzip \
  && addgroup tomcat && adduser -s /bin/bash -D -G tomcat tomcat \
  && mkdir -p /opt \
  && curl -LO https://archive.apache.org/dist/tomcat/tomcat-${TOMCAT_VERSION_MAJOR}/v${TOMCAT_VERSION_FULL}/bin/apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz \
  #  && curl -LO https://archive.apache.org/dist/tomcat/tomcat-${TOMCAT_VERSION_MAJOR}/v${TOMCAT_VERSION_FULL}/bin/apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz.md5 \
  #  && md5sum -c apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz.md5 \
  && gunzip -c apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz | tar -xf - -C /opt \
  #  && rm -f apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz.md5 \
  && ln -s /opt/apache-tomcat-${TOMCAT_VERSION_FULL} /opt/tomcat \
  && rm -rf /opt/tomcat/webapps/examples /opt/tomcat/webapps/docs /opt/tomcat/webapps/manager /opt/tomcat/webapps/host-manager \
  && apk del curl \
  && rm -rf /var/cache/apk/*

# Configure Tomcat users and CMIS secondary types
# Note: Tomcat users are not the same as CMIS-exposed users.
# CMIS users are specified in `default.properties` and parameterized by the
# downstream Dockerfile that specializes this base image for different business units (BUs).
# Each BU currently has different metadata schemas, though they may converge in the future.
ADD tomcat-users.xml /opt/tomcat/conf/
ADD default-types.xml /data/cmis/default-types.xml

# Declare the volume that contains persistent data: files and their associated
# metadata storage files (one-to-one in this implementation). Ensure this value
# stays consistent with the `persistenceDirectory` entry in `default.properties`.
VOLUME ["/data/cmis/default"]
# RUN set -x && chown -R tomcat:tomcat /data/cmis/default # Done in the entrypoint; ownership
# changes don't apply to a directory mounted at container start by Docker

# Set Tomcat location environment variables
ENV TOMCAT_BASE=/opt/tomcat
ENV CATALINA_HOME=/opt/tomcat

# Copy the WAR produced in the first build stage for deployment into Tomcat.
# The `VERSION` must match the one specified in `pom.xml`.
ENV VERSION=0.13.0-SNAPSHOT
COPY --from=build /usr/src/app/target/*.war /tmp/lightweightcmis-${VERSION}.war

# Prepare the required directories for the CMIS plugin
RUN set -x \
  && mkdir -p /data/cmis \
  && mkdir -p /data/log
RUN set -x \
  && mkdir ${TOMCAT_BASE}/webapps/lightweightcmis \
  && cd ${TOMCAT_BASE}/webapps/lightweightcmis \
  && unzip -qq /tmp/lightweightcmis-${VERSION}.war -d . \
  && chown -R tomcat:tomcat "$TOMCAT_BASE" \
  && chown -R tomcat:tomcat /data \
  && rm -fr /tmp/lightweightcmis-${VERSION}.war

# Configure container startup to run Tomcat.
# The entrypoint will handle parameterizing the service account.
# Run `dos2unix` to prevent Windows line endings from breaking Docker builds on Linux.
COPY docker-entrypoint.sh /
RUN dos2unix ./docker-entrypoint.sh && chmod 755 /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["catalina","run"]
