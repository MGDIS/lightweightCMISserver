#!/bin/sh

# A décommenter pour le debug, de façon à 1) sortir immédiatement sur une erreur, 2) émettre une erreur si une variable d'environnement n'est pas définie, et 3) afficher toutes les commandes dans le log
# set -eux

# Les variables d'environnement permettent de paramétrer les crédentiels
# La spécification du mot de passe est obligatoire pour des raisons de sécurité
SERVICE_ACCOUNT_LOGIN=${SERVICE_ACCOUNT_LOGIN:-admin}
if [ -z "$SERVICE_ACCOUNT_PASSWORD" ]; then
    echo "Error: SERVICE_ACCOUNT_PASSWORD must be specified using the -e flag (SERVICE_ACCOUNT_LOGIN can be used to alter default login, which is admin)."
    exit 1
fi
TOMCAT_LOGIN=${TOMCAT_LOGIN:-$SERVICE_ACCOUNT_LOGIN}
TOMCAT_PASSWORD=${TOMCAT_PASSWORD:-$SERVICE_ACCOUNT_PASSWORD}

# Mise à jour du fichier de configuration du compte utilisé pour la connexion au endpoint CMIS
# echo "Updating default.properties with login: $SERVICE_ACCOUNT_LOGIN and password: $SERVICE_ACCOUNT_PASSWORD"
sed -i "s|^user\.1 = .*|user\.1 = $SERVICE_ACCOUNT_LOGIN:$SERVICE_ACCOUNT_PASSWORD|" /opt/apache-tomcat-9.0.89/webapps/lightweightcmis/WEB-INF/classes/default.properties

# Mise à jour du fichier de configuration des utilisateurs Tomcat
# echo "Updating tomcat-users.xml with login: $TOMCAT_LOGIN and password: $TOMCAT_PASSWORD"
sed -i "s|<user name=\"[^\"]*\"|<user name=\"$TOMCAT_LOGIN\"|" /opt/tomcat/conf/tomcat-users.xml
sed -i "s|password=\"[^\"]*\"|password=\"$TOMCAT_PASSWORD\"|" /opt/tomcat/conf/tomcat-users.xml

# Drop root privileges if we are running catalina
# allows the container to be started with `--user`
if [ "$1" = 'catalina' -a "$(id -u)" = '0' ]; then
	chown -R tomcat:tomcat ${CATALINA_HOME}/* && chown -R tomcat:tomcat /data
	shift
	exec su-exec tomcat /opt/tomcat/bin/catalina.sh "$@"
else
	# As argument is not related to catalina,
	# then assume that user wants to run his own process,
	# for example a `bash` shell to explore this image
	exec "$@"
fi
