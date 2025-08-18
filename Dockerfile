FROM maven:3.9.9-eclipse-temurin-8 AS build
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
ADD pom.xml /usr/src/app
ADD . /usr/src/app
RUN ["mvn", "-e", "clean", "install"]
 
FROM openjdk:8-jre-alpine
 
# TOMCAT 
# Expose web port
EXPOSE 8080
 
# Tomcat Version
ENV TOMCAT_VERSION_MAJOR=9
ENV TOMCAT_VERSION_FULL=9.0.89
 
# Download and install
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
 
# Configuration
ADD tomcat-users.xml /opt/tomcat/conf/
ADD default-types.xml /data/cmis/default-types.xml

# Set environment
ENV TOMCAT_BASE=/opt/tomcat
ENV CATALINA_HOME=/opt/tomcat
 
# lightweightcmis 
 
ENV VERSION=0.13.0-SNAPSHOT
 
COPY --from=build /usr/src/app/target/*.war /tmp/lightweightcmis-${VERSION}.war
 
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
 
# Launch Tomcat on startup
 
COPY docker-entrypoint.sh /
 
RUN dos2unix ./docker-entrypoint.sh && chmod 755 /docker-entrypoint.sh
 
ENTRYPOINT ["/docker-entrypoint.sh"]
 
CMD ["catalina","run"]
