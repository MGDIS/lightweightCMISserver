# CMIS 1.1 compliant lightweight server

## Objective

**This implementation is a fork of Apache Chemistry OpenCMIS inMemory Server with file persistence!**

If you want a lightweight CMIS 1.1 server that allows custom types, secondary types and authentication support, this implementation is for you. It persists all content files and metadata files (*.metatdata* extension file) on hard drive. Owing to its memory-loading of metadata, it allows for large volumes of documents in queries in spite of the metadata being stored in files. Of course, loading time will be impacted with higher volumes of documents. Tests has shown working performance with 200 000 documents.

If you want to know more about the CMIS Standard, see https://www.oasis-open.org/committees/cmis.

The present project only covers a small part of the complete CMIS standard, but most of the basic functions. It can thus be used as a very simple CMS, without support for versions or any sophisticated commands. It supports only persisting documents with their metadata and retrieving them and their binary content through CMIS queries, which is what most software applications need first when they want to delegate document management. Once you need more advanced features, you can either extend the present project or turn to more complete implementations of the CMIS 1.1 norm, like Alfresco or Nuxeo for example.

## Configuration

### In memory implementation

The configuration file is `/src/main/webapp/WEB_INF/classes/repository.properties`. The following line performs what is necessary for the Apache Chemistry standard implementation of CMIS 1.1 to point at the mechanisms defined in the present project :

```bash
# ServiceFactory Implementation class
# Don't modify unless you know exactly what you are doing	
class=org.apache.chemistry.opencmis.inmemory.server.InMemoryServiceFactoryImpl
```	

All the configure repository need to have a dedicated properties file at /src/main/webapp/WEB_INF/classes/. The content of these repositories file is the same as `default.properties`.

### CMIS behaviour

There are many properties to configure for a repository. The most important ones are :

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

### Adding metadata schema

CMIS 1.1 supports additional metadata schema (secondary types). An example of a definition file for such secondary types is provided in the `default-types.xml` file, which is copied in the properties file in order to be active by default in the Docker version of the project.

You should first adjust the content of this file to your needs if you intend to use additional metadata schema (this is an option and you may use the server without it).

## Compilation

### Java source

It's a maven project. Just run :

```bash    
mvn clean install
```

### Docker

If you want to create a Docker image, run the following commands :

```sh
git clone https://github.com/MGDIS/lightweightCMISserver.git
cd lightweightCMISserver
docker build -t lightweightcmis .
```

## Install

### Inside Java server

To install WAR file, you should use a Tomcat or Jetty server. 
In Tomcat, just copy the war into /webapp directory and start your service. 
An index page will be available at(`http://localhost:8080/lightweightcmis`).

### Docker

Installation with Docker is a one-liner :

``` sh
docker run -d --name cms -p 8080:8080 lightweightcmis
```

Note that, in order to secure your files, you should use a volume based on the path indicated in the configuration file. **Warning : if nothing doing this, you will most certainly lose your documents !**

## Testing

### Bruno

A light set of automated test is provided in the `test/bruno` folder. This is a nice place to start with CMIS 1.1 REST API manipulations, as it provides you with the complete grammar that you will need to implement if you want to create your own client calling the operation supported by the project.

Bruno is a Postman-like software that helps running easily HTTP services. The provided collection roughly covers the CMIS 1.1 operations implemented by the lightweight CMIS server. CMIS standard covers many more functions, but this project only focuses on what is minimally needed to persist and expose files in a normalized way to any software service needing to delegate its document management.

### CMIS query

The following is an example of the queries you can use when operating the CMS with secondary types :

```sql
SELECT doc.cmis:objectId, doc.cmis:name, ns:refBusinessCase, ns:customerId 
FROM cmis:document AS doc 
LEFT OUTER JOIN ns:myschema AS ns ON doc.cmis:objectId = ns.cmis:objectId 
WHERE ns:customerId='xER76h9oP'
```

## CMIS client

There is a good CMIS client developed by Apache Chemistry. Just download the archive available here (`http://chemistry.apache.org/java/developing/tools/dev-tools-workbench.html`). Uncompress it and run `workbench.bat` or `workbench.sh` (depends on your platform). Note that client is often version dependant, so you may have to adjust to the version of Apache Chemistry used in the present code (currently 0.13).

The connection information are the following (please adjust to the changes if you made some in the customization) :

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

### Compliance

Workbench provides a Test Client Kit (TCK) to verify CMIS 1.1 conformity. Feel free to run it on this implementation !

### Proxyfied server

If you want to trace every HTTP exchange between client and server then just install Fiddler (http://www.telerik.com/fiddler) and change your server url :

```http	
URL : http://localhost.:8080/lightweightcmis/browser
```

> Note: Usage of this approach may slow down the server's response time! 
