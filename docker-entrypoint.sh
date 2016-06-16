#!/bin/sh

set -eux

# Drop root privileges if we are running catalina
# allow the container to be started with `--user`

if [ "$1" = 'catalina' -a "$(id -u)" = '0' ]; then

	# Change the ownership to tomcat
	chown -R tomcat:tomcat ${CATALINA_HOME}/* && chown -R tomcat:tomcat /data
	shift
	exec su-exec tomcat /opt/tomcat/bin/catalina.sh "$@"
else

	# As argument is not related to catalina,
	# then assume that user wants to run his own process,
	# for example a `bash` shell to explore this image
	exec "$@"

fi
