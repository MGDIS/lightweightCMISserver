FROM haraldkoch/alpine-tomcat7
MAINTAINER JLL "lelan-j@mgdis.fr"

ENV VERSION 0.13.0-SNAPSHOT

RUN mkdir -p /data/{logs,cmis}
ADD target/*.war /tmp/lightweightcmis-${VERSION}.war

ENV TOMCAT_BASE /usr/tomcat

# default user
RUN addgroup tomcat && adduser -s /bin/bash -D -G tomcat tomcat

RUN mkdir ${TOMCAT_BASE}/webapps/lightweightcmis \
        && cd ${TOMCAT_BASE}/webapps/lightweightcmis \
        && unzip -qq /tmp/lightweightcmis-${VERSION}.war -d . \
        && chown -R tomcat:tomcat "$TOMCAT_BASE" \
        && chown -R tomcat:tomcat /data \
        && rm -fr /tmp/lightweightcmis-${VERSION}.war

ENV GOSU_VERSION 1.7
RUN set -x \
    && apk add --no-cache --virtual .gosu-deps \
        dpkg \
        gnupg \
        openssl \
    && wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$(dpkg --print-architecture)" \
    && wget -O /usr/local/bin/gosu.asc "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$(dpkg --print-architecture).asc" \
    && export GNUPGHOME="$(mktemp -d)" \
    && gpg --keyserver ha.pool.sks-keyservers.net --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 \
    && gpg --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu \
    && rm -r "$GNUPGHOME" /usr/local/bin/gosu.asc \
    && chmod +x /usr/local/bin/gosu \
    && gosu nobody true \
    && apk del .gosu-deps

ENTRYPOINT ["gosu", "tomcat", "/usr/local/bin/run"]
