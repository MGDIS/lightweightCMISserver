# CMIS 1.1 compliant lightweight server #


**This implementation is a fork of Apache Chemistry OpenCMIS inMemory Server (version 0.12).**

If you want a lightweight CMIS 1.1 server that allows custom types, secondary types and authentication support. This implementation is for you.

It persists all content files and metadata files (*.metatdata* extension file) on hard drive. 

If you want to know more about the CMIS Standard (see https://www.oasis-open.org/committees/cmis)

## Compilation ##
It's a maven project. Just run
    
    mvn clean install

## Usage ##
The configuration file is /src/main/webapp/WEB_INF/classes/repository.properties.

	# ServiceFactory Implementation class
	# Don't modify unless you know exactly what you are doing	
	class=org.apache.chemistry.opencmis.inmemory.server.InMemoryServiceFactoryImpl

	# List of repositories 's file path that define each repository in details  
	InMemoryServer.repositories=/A1.properties,/A2.properties

Repository definition
==============

	# Authentication mode
	auth.mode=basic

	# List of authenticated users
	user.1 = test:test
	user.2 = reader:reader
	user.3 = admin:admin
	
	# In Memory Settings
	InMemoryServer.RepositoryId=A1
	InMemoryServer.TypeDefinitionsFile=/types.xml
	InMemoryServer.Class=org.apache.chemistry.opencmis.inmemory.storedobj.impl.StoreManagerImpl
	InMemoryServer.TempDir=/temp/cmis/A1

	# settings for init repository with data
	RepositoryFiller.Enable=false
	# Type id of documents that are created
	RepositoryFiller.DocumentTypeId=cmis:document
	# Type id of folders that are created
	RepositoryFiller.FolderTypeId=cmis:folder
	# Number of documents created per folder
	RepositoryFiller.DocsPerFolder=3
	# Number of folders created per folder
	RepositoryFiller.FolderPerFolder=2
	# number of folder levels created (depth of hierarchy)
	RepositoryFiller.Depth=3
	# Size of content for documents (0=do not create content), default=0
	RepositoryFiller.ContentSizeInKB=32
	# properties to set for a document
	#RepositoryFiller.DocumentProperty.0=StringProp
	#RepositoryFiller.DocumentProperty.1=StringPropMV
	# properties to set for a folder
	#RepositoryFiller.FolderProperty.0=StringFolderProp
	# InMemoryServer.MaxContentSizeKB=4096
	# InMemoryServer.CleanIntervalMinutes=240
	RepositoryFiller.ContentKind=lorem/text
	# RepositoryFiller.ContentKind=lorem/html
	# RepositoryFiller.ContentKind=static/text
	# RepositoryFiller.ContentKind=fractal/jpeg
	# slow!! 
 
 

## Installation ##
To install war file, you should use a Tomcat or Jetty server. 
In Tomcat, just copy the war into /webapp directory and start your service. 
An index page will be available at(http://localhost:8080/chemistry-opencmis-server).

## Testing ##
There is a good CMIS client developed by Apache Chemistry. 
Just download the archive available here (http://chemistry.apache.org/java/developing/tools/dev-tools-workbench.html). 
Uncompress it and run workbench.bat or workbench.sh (dependent of your platform).
    
    URL : http://localhost:8080/server/browser
    Binding : Browser
    Username : test
    Password : test
    Authentication : Standard
    Compression : On
    Client Compression : Off
    Cookies : On

Test Client Kit
==========
Workbench provides a Test Client Kit (TCK) to verify CMIS 1.1 conformity. Feel free to run it on this implementation!

Proxyfied server
=========
If you want to trace every HTTP exchange between client and server then just install Fiddler (http://www.telerik.com/fiddler) and change your server url
	
	URL : http://localhost.:8080/server/browser


> Note: Usage of this URL may slowed down the server's response time! 
