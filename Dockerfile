FROM haraldkoch/alpine-tomcat7
MAINTAINER JLL "lelan-j@mgdis.fr"

ADD target/*.war /tmp/chemistry-opencmis.war

ENV TOMCAT_BASE /usr/tomcat

# default user
RUN addgroup tomcat && adduser -s /bin/bash -D -G tomcat tomcat

RUN mkdir ${TOMCAT_BASE}/webapps/chemistry-opencmis \
        && cd ${TOMCAT_BASE}/webapps/chemistry-opencmis \
        && unzip -qq /tmp/chemistry-opencmis.war -d . \
        && chown -R tomcat:tomcat "$TOMCAT_BASE" \
        && chown -R tomcat:tomcat /data \
        && chown -R tomcat:tomcat /conf

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
