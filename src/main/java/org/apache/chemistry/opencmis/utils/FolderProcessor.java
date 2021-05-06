package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.concurrent.ExecutorService;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyStringImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.DocumentImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.FolderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolderProcessor{
    private static final Logger LOG = LoggerFactory
            .getLogger(FilePersistenceLoader.class.getName());
    
    public static final String SUFFIXE_METADATA = ".metadata";
    public static final String SHADOW_EXT = ".cmis.xml";
    public static final String SHADOW_FOLDER = "cmis.xml";
    
    private final ExecutorService pool;

    public FolderProcessor(ExecutorService pool){
        this.pool = pool;
    }

    void loadFolder(String repositoryId, ObjectStore store,
            File folder, String folderId, FilenameFilter filenameFilter,
            PersistenceManager persistenceManager){
    	
    	LOG.debug("Scanning " + folder.getAbsolutePath());
        
    	// iterate through children
        for (File child : folder.listFiles(filenameFilter)) {
        	// skip hidden files
            if (child.isHidden()) continue;
            // process
        	pool.execute(() -> manageChild(repositoryId, store, folder, folderId, filenameFilter, persistenceManager, child));
        }
    }
    
    void manageChild(String repositoryId, ObjectStore store,
            File folder, String folderId, FilenameFilter filenameFilter,
            PersistenceManager persistenceManager, File child) {
    	
    	LOG.debug("Loading file " + child.getAbsolutePath());
    	
    	boolean toBeSaved = false;
    	
        StoredObject so = null;
        if (child.isDirectory()) {
            so = new FolderImpl(child.getName(), folderId);
            so.setName(child.getName());
            so.setId(persistenceManager.generateId());
            so.setRepositoryId(repositoryId);
            so.setStore(store);
            File metadataFile = new File(child.getAbsolutePath()
                    + SUFFIXE_METADATA);
            
            String relativePath = child.getAbsolutePath().replace(persistenceManager.getRootPath(), "");
            
            SimpleImmutableEntry<Boolean, StoredObject> unmarshalled = persistenceManager
                    .readCMISFromDisk(metadataFile, store);
            if (unmarshalled != null) {
                FolderImpl meta = (FolderImpl) unmarshalled.getValue();
                toBeSaved = unmarshalled.getKey();
                // if metadata file exists then meta is the CMIS Object
                so = meta;
                so.setRepositoryId(repositoryId);
                so.setStore(store);
            } else {
                LOG.debug("Missing metadata or malformed file for " + child.getAbsolutePath());
                so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                so.setTypeId(BaseTypeId.CMIS_FOLDER.value());
                so.setName(child.getName());
                toBeSaved = true;
            }
            Map<String, PropertyData<?>> properties = so.getProperties();
            if (!properties.containsKey(PropertyIds.PATH) 
                    || !properties.get(PropertyIds.PATH).getFirstValue().equals(relativePath)) {
                LOG.debug("Fixing cmis:path for " + relativePath);
                properties.put(PropertyIds.PATH, new PropertyStringImpl(PropertyIds.PATH, relativePath));
                toBeSaved = true;
            }
            Folder fso = (Folder) so;
            if (fso.getTypeId() == null) fso.setTypeId("cmis:folder");
            if (!folderId.equals(fso.getParentId())) {
                LOG.debug("Fixing folder.parentId : setting " + folderId + " into cmis:parentId");
                fso.setParentId(folderId);
                toBeSaved = true;
            }
        } else {
            so = new DocumentImpl();
            File metadataFile = new File(child.getAbsolutePath()
                    + SUFFIXE_METADATA);
            so.setId(persistenceManager.generateId());
            so.setRepositoryId(repositoryId);
            so.setStore(store);

            SimpleImmutableEntry<Boolean, StoredObject> unmarshalled = persistenceManager
                    .readCMISFromDisk(metadataFile, store);
            
            if (unmarshalled != null) {
                DocumentImpl meta = (DocumentImpl) unmarshalled.getValue();
                toBeSaved = unmarshalled.getKey();
                // if metadata file exists then meta is the CMIS Object
                so = meta;
            } else {
                LOG.debug("Missing metadata or malformed file for " + child.getAbsolutePath());
                so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                so.setName(child.getName());
                so.setTypeId(BaseTypeId.CMIS_DOCUMENT.value());
                toBeSaved = true;
            }
            DocumentImpl dso = (DocumentImpl) so;
            if(dso.getTypeId() == null) dso.setTypeId("cmis:document");
            if (dso.getParentIds() == null || 
                    dso.getParentIds().size() == 0 || 
                    !dso.getParentIds().contains(folderId)) {
                LOG.debug("Fixing document.parentIds : setting " + folderId + " into cmis:parentIds");
                /*
                 * This is a bug fixing patch, in order to fix the orphan documents by adding
                 * their current physical directory to the list of ParentIds. 
                 * but we can't know if the orphan situation comes from a 'move' or a 'addToFolder' bug
                 * therefore it is impossible to state if we have to set the current directory 
                 * to a unique parentId or just add the missing parentId
                 * 
                 * By default it will be : set the current directory to a unique parentId
                 * 
                */
                dso.getParentIds().clear();
                dso.getParentIds().add(0, folderId);
                toBeSaved = true;
            }
            // read file attributes without stream
            dso.setContent(persistenceManager.readFileAttributes(child));
        }
        // save metadata to disk
        if (toBeSaved) {
            persistenceManager.writeCMISToDisc(child, so);
        }
        // save object in memory
        store.storeObject(so, false);
        // invoke recursiveness if needed
        if (child.isDirectory()) {
            // recursive loading
        	String soId = so.getId();
            loadFolder(repositoryId, store, child, soId, filenameFilter, persistenceManager);
        }
    }
}
