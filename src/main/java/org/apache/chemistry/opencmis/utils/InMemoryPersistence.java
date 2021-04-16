package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.AbstractMap.SimpleImmutableEntry;

import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;

public class InMemoryPersistence extends PersistenceManager {

    
    public void setRootPath(String rootPath) {
        // TODO Auto-generated method stub

    }

    
    public String getRootPath() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public String getRootId() {
        // TODO Auto-generated method stub
        return null;
    }

    
    public File getFile(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public File getMetadataFile(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public ContentStream readContent(File file, boolean closeOnEnd) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public int writeContent(File newFile, InputStream stream) {
        // TODO Auto-generated method stub
    	return 0;
    }

    
    public String getId(StoredObject so) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public String getId(File file) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public void writeCMISToDisc(File newFile, StoredObject so) {
        // TODO Auto-generated method stub

    }

    
    public SimpleImmutableEntry<Boolean, StoredObject> readCMISFromDisk(File metadataFile, ObjectStore store) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public void deleteFromDisk(StoredObject so) {
        // TODO Auto-generated method stub

    }

    
    public File calculateFile(Map<String, StoredObject> storedObjectMap,
            StoredObject so) {
        // TODO Auto-generated method stub
        return null;
    }

    
    public void saveObject(Map<String, StoredObject> storedObjectMap,
            StoredObject so, boolean withContent) {
        // TODO Auto-generated method stub

    }

	
	public File getFile(StoredObject so, Map<String, StoredObject> storedObjectMap) {
		// TODO Auto-generated method stub
		return null;
	}


	public InputStream getStream(File file) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void moveObject(Map<String, StoredObject> storedObjectMap,
			StoredObject so, Folder newParent) throws IOException {
		// TODO Auto-generated method stub
		
	}
}