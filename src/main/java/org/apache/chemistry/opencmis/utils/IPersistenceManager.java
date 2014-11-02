package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;

public interface IPersistenceManager {

    public void setRootPath(String rootPath);

    public String getRootPath();

    public File getFile(String id);

    public File getMetadataFile(String id);

    public ContentStream readContent(File file);

    public void writeContent(File newFile, InputStream stream);

    public String getId(StoredObject so);

    public String getId(File file);

    public void writeCMISToDisc(File newFile, StoredObject so);

    public StoredObject readCMISFromDisk(File metadataFile);

    public void deleteFromDisk(StoredObject so);

    public File calculateFile(Map<String, StoredObject> storedObjectMap,
            StoredObject so);

    public void saveObject(Map<String, StoredObject> storedObjectMap,
            StoredObject so, boolean withContent);
}