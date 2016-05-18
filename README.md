# CMIS 1.1 compliant lightweight server
### Status
[![Build Status](https://travis-ci.org/johanlelan/lightweightCMISserver.svg?branch=master)](https://travis-ci.org/MGDIS/lightweightCMISserver) [![Coverage Status](https://img.shields.io/coveralls/johanlelan/lightweightCMISserver.svg)](https://coveralls.io/r/johanlelan/lightweightCMISserver)

**This implementation is a fork of Apache Chemistry OpenCMIS inMemory Server with file persistence!**

If you want a lightweight CMIS 1.1 server that allows custom types, secondary types and authentication support. This implementation is for you.

It persists all content files and metadata files (*.metatdata* extension file) on hard drive. 

If you want to know more about the CMIS Standard (see https://www.oasis-open.org/committees/cmis)

## Compilation
It's a maven project. Just run
```bash    
mvn clean install
```
## Usage
The configuration file is `/src/main/webapp/WEB_INF/classes/repository.properties`.
```bash
# ServiceFactory Implementation class
# Don't modify unless you know exactly what you are doing	
class=org.apache.chemistry.opencmis.inmemory.server.InMemoryServiceFactoryImpl

# List of repositories file path that define each repository in details  
```	
All the configure repository need to have a dedicated properties file at /src/main/webapp/WEB_INF/classes/. The content of these reposiroties file is the same as `default.properties`.

In the next section, you have to configure each repository.

Repository definition
==============
There are many properties to configure for a repository. The most important ones are :
- auth.mode
- InMemoryServer.RepositoryId
- persistenceDirectory

```bash
# Authentication mode
auth.mode=basic

# List of authenticated users
user.1 = test:test
user.2 = reader:reader
user.3 = admin:admin

# In Memory Settings
InMemoryServer.RepositoryId=default
InMemoryServer.TypeDefinitionsFile=/data/cmis/default-types.xml
InMemoryServer.Class=org.apache.chemistry.opencmis.inmemory.storedobj.impl.StoreManagerImpl
InMemoryServer.TempDir=/temp/cmis/A1

# settings for init repository with data
persistenceDirectory=/data/cmis/default
```	

## Installation
To install war file, you should use a Tomcat or Jetty server. 
In Tomcat, just copy the war into /webapp directory and start your service. 
An index page will be available at(`http://localhost:8080/lightweightcmis`).

## Testing
There is a good CMIS client developed by Apache Chemistry. 
Just download the archive available here (`http://chemistry.apache.org/java/developing/tools/dev-tools-workbench.html`). 
Uncompress it and run `workbench.bat` or `workbench.sh` (depends on your platform).
```bash    
URL : http://localhost:8080/lightweightcmis/browser
Binding : Browser
Username : test
Password : test
Authentication : Standard
Compression : On
Client Compression : Off
Cookies : On
```
### Test Client Kit
Workbench provides a Test Client Kit (TCK) to verify CMIS 1.1 conformity. Feel free to run it on this implementation!

### Proxyfied server
If you want to trace every HTTP exchange between client and server then just install Fiddler (http://www.telerik.com/fiddler) and change your server url
```http	
URL : http://localhost.:8080/lightweightcmis/browser
```

> Note: Usage of this URL may slowed down the server's response time! 

## Docker
If you want to create a docker image.
```sh
git clone https://github.com/MGDIS/lightweightCMISserver.git
cd lightweightCMISserver
docker build -t lightweightcmis .
```
