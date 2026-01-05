#!/bin/sh

# Uncomment for debugging to 1) exit immediately on error, 2) treat unset variables as errors, and 3) print commands as they are executed
# set -eux

# Environment variables are used to configure credentials
# Providing a password is mandatory for security reasons
SERVICE_ACCOUNT_LOGIN=${SERVICE_ACCOUNT_LOGIN:-admin}
if [ -z "$SERVICE_ACCOUNT_PASSWORD" ]; then
    echo "Error: SERVICE_ACCOUNT_PASSWORD must be specified using the -e flag (SERVICE_ACCOUNT_LOGIN can be used to alter default login, which is admin)."
    exit 1
fi
TOMCAT_LOGIN=${TOMCAT_LOGIN:-$SERVICE_ACCOUNT_LOGIN}
TOMCAT_PASSWORD=${TOMCAT_PASSWORD:-$SERVICE_ACCOUNT_PASSWORD}

# Update the configuration file that defines the account used to connect to the CMIS endpoint
# echo "Updating default.properties with login: $SERVICE_ACCOUNT_LOGIN and password: $SERVICE_ACCOUNT_PASSWORD"
sed -i "s|^user\.1 = .*|user\.1 = $SERVICE_ACCOUNT_LOGIN:$SERVICE_ACCOUNT_PASSWORD|" /opt/apache-tomcat-9.0.89/webapps/lightweightcmis/WEB-INF/classes/default.properties

# Update the Tomcat users configuration file
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
