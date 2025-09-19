# TODO

Though the current project implements the most important CMIS 1.1 commands and supports secondary types, which is enough for most uses, there are some additions that could be interesting. They are listed below.

## OpenID Connect for authentification via Keycloak

The CMIS endpoint is for now only secured with Basic Authentication. It could be valuable to secure it with OAuth 2.0. A few approaches have already been studied and are exposed below to ease future work.

### Keycloak Tomcat Adapter

To add the adapter you need to, in the Dockerfile, download the zip from `https://mvnrepository.com/artifact/org.keycloak/keycloak-tomcat-core-adapter/25.0.3`, and move the content to `/opt/tomcat/lib/`.

```dockerfile
ENV KEYCLOAK_VERSION=25.0.3

RUN wget -P /tmp https://repo1.maven.org/maven2/org/keycloak/keycloak-tomcat-adapter-dist/${KEYCLOAK_VERSION}/keycloak-tomcat-adapter-dist-${KEYCLOAK_VERSION}.zip \
  && unzip /tmp/keycloak-tomcat-adapter-dist-${KEYCLOAK_VERSION}.zip -d /tmp/keycloak-tomcat-adapter-dist \
  && mv /tmp/keycloak-tomcat-adapter-dist/* /opt/tomcat/lib/ \
  && rm /tmp/keycloak-tomcat-adapter-dist-${KEYCLOAK_VERSION}.zip \
  && rm -rf /tmp/keycloak-tomcat-adapter-dist/
```

In your Keycloak, retrieve the `keycloak.json` and copy it in `lightweightcmis/WEB-INF`. This would something like:

```json
{
  "realm": ,
  "auth-server-url": ,
  "ssl-required": ,
  "resource": ,
  "public-client": ,
  "confidential-port":
}
```

In the `WEB-INF/web.xml` or `server.xml`, declare the restricted zone with url patterns like this:

```xml
<login-config>
    <auth-method>KEYCLOAK</auth-method>
    <realm-name>your-realm-name</realm-name>
</login-config>

<security-constraint>
    <web-resource-collection>
        <web-resource-name>secure</web-resource-name>
        <url-pattern>/secure/*</url-pattern>
    </web-resource-collection>
    <auth-constraint>
        <role-name>keycloak-role</role-name>
    </auth-constraint>
</security-constraint>
```

In order to redirect the authentication process, you have to create the `context.xml` in `lightweightcmis/META-INF/` and specify:

```xml
<Context>
  <Valve className="org.keycloak.adapters.tomcat.KeycloakAuthenticatorValve"/>
</Context>
```

**Point of failure :** the `web.xml` file doesn't exist in the source files of LightweightCMISServer, it must be created by the compilation. Overwriting the file with the modifications in the Dockerfile doesn't break the compilation and the tomcat still runs but it causes a 404 error at http://localhost:8080/lightweightcmis.

To add on, this method isn't a good practice because Keycloak has depreciated the adapters. It would be a better solution to find a OpenID Connect client directly.

### OpenID Connect Authenticator / tomcat-oidcauth

Another approach is to use `https://mvnrepository.com/artifact/org.bsworks.catalina.authenticator.oidc/tomcat-oidcauth`. It was said in the documentation to add this dependency to the `pom.xml`.

```xml
<dependency>
    <groupId>org.bsworks.catalina.authenticator.oidc</groupId>
    <artifactId>tomcat-oidcauth</artifactId>
    <version>2.3.0</version>
    <type>pom</type>
</dependency>
```

But it wasn't found because the repository isn't a default one for maven. It was thus added as a new repository in the `pom.xml` file:

```xml
<repositories>
  <repository>
    <id>boyle-software-releases</id>
    <name>Boyle Software Releases</name>
    <url>https://maven.boylesoftware.com/maven/repo-os/</url>
  </repository>
</repositories>
```

It solved the issue in VSCode but not for the `docker build` which still couldn't find it.

### Approach with pac4j

To use pac4j for openID Connect, you need three dependencies : `pac4j-core`, `pac4j-oidc` and `pac4j-servlet`. But the Docker image is based on `openjdk:8-jre-alpine`, so it's a Java 8 image. The correspondant versions for `pac4j` are the 4.x, but the `pac4j-servlet` has been changed to `pac4j-javaee` and there is none for the 4.x versions. And the maven repository doesn't have anything for `pac4j-servlet`.

This seems like a dead end for now.

### Reverse proxy with Caddy

Perhaps the simplest approach is to let a reverse proxy like Caddy, in addition with TLS exposition, to handle the authentication. In this case, we could simply deactivate authentication at Tomcat level on the endpoint and let Caddy do the work.

This should be studied with the production team.

## S3 storage for files

Storing files in an S3 endpoint would be an interesting approach to handle file redundancy and cloudification.

### AWS SDK for Java

A first try was made with the AWS SDK for Java available at `https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk` but caused too many compilation problems.

### MinIO

Another S3 client, maybe more simple, could be MinIO. It has not been tested yet, but we already know it should be added in the `pom.xml` file:

```xml
<dependency>
     <groupId>io.minio</groupId>
     <artifactId>minio</artifactId>
     <version>8.5.1</version>
</dependency>
```

And here is a code example from Gemini to upload a file:

```java
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class MinioS3Uploader {

     public static void main(String[] args) throws IOException,
NoSuchAlgorithmException, InvalidKeyException {
         try {
             // Création du client MinIO pour se connecter à un service
compatible S3
             MinioClient minioClient = MinioClient.builder()
                     .endpoint("https://s3.amazonaws.com") // Point de
terminaison S3 AWS
                     .region("eu-west-3") // Ta région
                     .credentials("VOTRE_ACCESS_KEY_ID",
"VOTRE_SECRET_ACCESS_KEY") // Tes identifiants AWS
                     .build();

             // Nom du bucket et du fichier
             String bucketName = "votre-nom-de-bucket";
             String objectName = "fichier-upload.txt";
             String filePath =
"/chemin/vers/votre/fichier/local/fichier.txt";

             // Vérifie si le bucket existe, sinon le crée
             boolean found =
minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
             if (!found) {
minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
                 System.out.println("Bucket " + bucketName + " créé avec
succès.");
             }

             // Téléchargement du fichier
             try (InputStream is = new FileInputStream(filePath)) {
                 minioClient.putObject(
                         PutObjectArgs.builder()
                                 .bucket(bucketName)
                                 .object(objectName)
                                 .stream(is, is.available(), -1)
                                 .contentType("text/plain") // Type MIME
du fichier
                                 .build());

                 System.out.println("Fichier " + objectName + "
téléchargé avec succès dans le bucket " + bucketName);
             }
         } catch (MinioException e) {
             System.out.println("Erreur survenue : " + e);
             System.out.println("HTTP Trace: " + e.httpTrace());
         }
     }
}
```

## SQL database for metadata storage

The current drawback of the local file implementation for metadata storage is the time required to load all metadata in the hashmap when the application starts (this is needed for good query performance). Though this is not a show-stopper, handling metadata in a relational database would help.

Since CMIS queries are based on an SQL grammar, there seems to be a great alignment, but care should be taken on a strong alignment of metadata schema and columns in the database, as this would couple the persistance and schema definition. In most case, this could be acceptable if ascending compatibility is handled with additional SQL columns in the data table, but if the metadata schema requires more dynamicity, the mapping should be adjusted and the code would be much harder to implement, because each step in the visitor pattern handling the query should be adjusted.
