FROM openjdk:8-jre-alpine
MAINTAINER JLL "lelan-j@mgdis.fr"

# TOMCAT 
# Expose web port
EXPOSE 8080

# Tomcat Version
ENV TOMCAT_VERSION_MAJOR 9
ENV TOMCAT_VERSION_FULL  9.0.89

# Download and install
RUN set -x \
  && apk add --no-cache su-exec \
  && apk add --update curl \
  && addgroup tomcat && adduser -s /bin/bash -D -G tomcat tomcat \
  && mkdir -p /opt \
  && curl -LO https://archive.apache.org/dist/tomcat/tomcat-${TOMCAT_VERSION_MAJOR}/v${TOMCAT_VERSION_FULL}/bin/apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz \
  && gunzip -c apache-tomcat-${TOMCAT_VERSION_FULL}.tar.gz | tar -xf - -C /opt \
  && ln -s /opt/apache-tomcat-${TOMCAT_VERSION_FULL} /opt/tomcat \
  && rm -rf /opt/tomcat/webapps/examples /opt/tomcat/webapps/docs /opt/tomcat/webapps/manager /opt/tomcat/webapps/host-manager \
  && apk del curl \
  && rm -rf /var/cache/apk/*

# Configuration
ADD tomcat-users.xml /opt/tomcat/conf/

# Set environment
ENV TOMCAT_BASE /opt/tomcat
ENV CATALINA_HOME /opt/tomcat

# lightweightcmis 

#ENV VERSION 0.13.0-SNAPSHOT
ENV VERSION 0.12.18

RUN set -x \
    && mkdir -p /data/cmis \
    && mkdir -p /data/log

ADD target/*.war /tmp/lightweightcmis-${VERSION}.war

RUN set -x \
	&& mkdir ${TOMCAT_BASE}/webapps/lightweightcmis \
        && cd ${TOMCAT_BASE}/webapps/lightweightcmis \
        && unzip -qq /tmp/lightweightcmis-${VERSION}.war -d . \
        && chown -R tomcat:tomcat "$TOMCAT_BASE" \
        && chown -R tomcat:tomcat /data \
        && rm -fr /tmp/lightweightcmis-${VERSION}.war

# Launch Tomcat on startup

COPY docker-entrypoint.sh /

RUN chmod 755 /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["catalina","run"]
