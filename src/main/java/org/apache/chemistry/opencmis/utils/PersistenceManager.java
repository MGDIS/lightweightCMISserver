package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.AbstractMap.SimpleImmutableEntry;

import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;

public abstract class PersistenceManager {

    public abstract void setRootPath(String rootPath);

    public abstract String getRootPath();
    public abstract String getRootId();

    public abstract File getFile(String id);

    public abstract File getFile(StoredObject so, Map<String, StoredObject> storedObjectMap);
    
    public abstract File getMetadataFile(String id);

    public abstract InputStream getStream(File file);
    
    public abstract ContentStream readContent(File file, boolean closeOnEnd);

    public abstract int writeContent(File newFile, InputStream stream);

    public abstract String getId(StoredObject so);

    public abstract String getId(File file);

    public abstract void writeCMISToDisc(File newFile, StoredObject so);

    public abstract SimpleImmutableEntry<Boolean, StoredObject> readCMISFromDisk(File metadataFile, ObjectStore store);

    public abstract void deleteFromDisk(StoredObject so);

    public abstract File calculateFile(Map<String, StoredObject> storedObjectMap,
            StoredObject so);

    public abstract void saveObject(Map<String, StoredObject> storedObjectMap,
            StoredObject so, boolean withContent);
    
    public abstract void moveObject(Map<String, StoredObject> storedObjectMap, 
    		StoredObject so, Folder newParent) throws IOException;
    
    /*
     * Return the internal identifier of this document
     */
    public String generateId() {
        return UUID.randomUUID().toString();
    }
}