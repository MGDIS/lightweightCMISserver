package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;

public class InMemoryPersistence implements IPersistenceManager {

    @Override
    public void setRootPath(String rootPath) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getRootPath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public File getFile(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public File getMetadataFile(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ContentStream readContent(File file) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void writeContent(File newFile, InputStream stream) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getId(StoredObject so) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getId(File file) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void writeCMISToDisc(File newFile, StoredObject so) {
        // TODO Auto-generated method stub

    }

    @Override
    public StoredObject readCMISFromDisk(File metadataFile) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deleteFromDisk(StoredObject so) {
        // TODO Auto-generated method stub

    }

    @Override
    public File calculateFile(Map<String, StoredObject> storedObjectMap,
            StoredObject so) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void saveObject(Map<String, StoredObject> storedObjectMap,
            StoredObject so, boolean withContent) {
        // TODO Auto-generated method stub

    }

}