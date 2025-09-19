# Le premier stage du Dockerfile utilise une image Maven pour compiler le plugin
# de persistence obéissant à l'API spécifiée par Apache Chemistry OpenCMIS
FROM maven:3.9.9-eclipse-temurin-8 AS build
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
ADD pom.xml /usr/src/app
ADD src/ /usr/src/app/src
RUN ["mvn", "-e", "clean", "install"]

# Le second stage utilise une image OpenJDK allégée sur laquelle on va déployer
# Tomcat, le plugin compilé en premier stage, et paramétrer le tout
FROM openjdk:8-jre-alpine

# Déclaration du port d'écoute
EXPOSE 8080

# Téléchargement et déploiement de la version de Tomcat validée
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
 
# Configuration des utilisateurs Tomcat et des types secondaires CMIS
# Attention, les utilisateurs Tomcat ne sont pas la même chose que les utilisateurs d'exposition CMIS
# Ces derniers sont spécifiés dans le fichier default.properties, et variabilisés par le Dockerfile
# de second niveau, qui spécialise la présente image de base pour les différentes BU, qui ont à ce jour
# des schémas de métadonnées différents, même s'il pourrait y avoir convergence à terme
ADD tomcat-users.xml /opt/tomcat/conf/
ADD default-types.xml /data/cmis/default-types.xml

# On déclare le volume qui contient les données à sauvegarder, à savoir les fichiers et les fichiers
# de stockage des métadonnées associés (un pour un dans cette implémentation) ; attention à bien garder
# cette valeur en cohérence avec l'entrée persistenceDirectory du fichier default.properties
VOLUME ["/data/cmis/default"]
# RUN set -x && chown -R tomcat:tomcat /data/cmis/default # Fait dans l'entrypoint, sinon on n'agit pas sur le répertoire monté à la volée par Docker lors du démarrage du conteneur

# Paramétrage du positionnement de Tomcat
ENV TOMCAT_BASE=/opt/tomcat
ENV CATALINA_HOME=/opt/tomcat

# Recopie du WAR produit en premier stage de compilation Docker pour déploiement dans Tomcat sur la version spécifiée
# Cette version doit être en cohérence avec celle spécifiée dans le fichier pom.xml
ENV VERSION=0.13.0-SNAPSHOT
COPY --from=build /usr/src/app/target/*.war /tmp/lightweightcmis-${VERSION}.war

# Préparation des répertoires nécessaires pour le plugin CMIS
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

# Paramétrage du démarrage du conteneur, qui doit lancer Tomcat
# C'est l'entrypoint qui se chargera de la variabilisation du compte de service
# Le dos2unix est nécessaire pour que l'encodage Windows ne casse pas la compilation Docker sur une image Linux
COPY docker-entrypoint.sh /
RUN dos2unix ./docker-entrypoint.sh && chmod 755 /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["catalina","run"]
