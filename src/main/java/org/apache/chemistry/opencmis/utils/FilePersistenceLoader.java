package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyStringImpl;
import org.apache.chemistry.opencmis.inmemory.ConfigConstants;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoreManager;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.DocumentImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.FolderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilePersistenceLoader {

    private static final Logger LOG = LoggerFactory
            .getLogger(FilePersistenceLoader.class.getName());

    public static final String SUFFIXE_METADATA = ".metadata";
    public static final String SHADOW_EXT = ".cmis.xml";
    public static final String SHADOW_FOLDER = "cmis.xml";

    public static void loadDirectory(StoreManager storeManager,
            Map<String, String> parameters) {
        String repositoryId = parameters.get(ConfigConstants.REPOSITORY_ID);
        ObjectStore store = storeManager.getObjectStore(repositoryId);
        String dir = parameters.get(ConfigConstants.TEMP_DIR);
        LOG.info("Start Scanning directory...");
        // get the folder
        File folder = new File(dir);
        if (!folder.isDirectory()) {
        	folder.mkdirs();
            //throw new CmisObjectNotFoundException(folder.getAbsolutePath()
            //        + " is not a folder!");
        }

        FilenameFilter filenameFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return !name.endsWith(SUFFIXE_METADATA) && !name.endsWith(SHADOW_EXT) && !name.equals(SHADOW_FOLDER);
            }
        };
        PersistenceManager manager = storeManager
                .getObjectStore(repositoryId).getPersistenceManager();
        loadFolder(repositoryId, store, folder, manager.getRootId(), filenameFilter, manager);

        LOG.info("... End Scanning");
    }

    private static void loadFolder(String repositoryId, ObjectStore store,
            File folder, String folderId, FilenameFilter filenameFilter,
            PersistenceManager persistenceManager) {
        LOG.info("Scanning " + folder.getAbsolutePath());
        // iterate through children
        for (File child : folder.listFiles(filenameFilter)) {
            // skip hidden files
            if (child.isHidden()) continue;

        	LOG.debug("Loading file " + child.getAbsolutePath());
        	
        	boolean toBeSaved = false;
        	
            StoredObject so = null;
            if (child.isDirectory()) {
                so = new FolderImpl(child.getName(), folderId);
                so.setName(child.getName());
                so.setId(persistenceManager.generateId());
                File metadataFile = new File(child.getAbsolutePath()
                        + SUFFIXE_METADATA);
                
                String relativePath = child.getAbsolutePath().replace(persistenceManager.getRootPath(), "");
                
                FolderImpl meta = (FolderImpl) persistenceManager
                        .readCMISFromDisk(metadataFile);
                if (meta != null) {
                    // if metadata file exists then meta is the CMIS Object
                    so = meta;
                } else {
                    LOG.warn("Missing metadata file for " + child.getAbsolutePath());
                    so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                    so.setTypeId(BaseTypeId.CMIS_FOLDER.value());
                    so.setName(child.getName());
                    toBeSaved = true;
                }
                Map<String, PropertyData<?>> properties = so.getProperties();
                if (!properties.containsKey(PropertyIds.PATH) 
                        || !properties.get(PropertyIds.PATH).getFirstValue().equals(relativePath)) {
                    LOG.warn("Fixing cmis:path for " + relativePath);
                    properties.put(PropertyIds.PATH, new PropertyStringImpl(PropertyIds.PATH, relativePath));
                    toBeSaved = true;
                }
                Folder fso = (Folder) so;
                if (fso.getTypeId() == null) fso.setTypeId("cmis:folder");
                if (!folderId.equals(fso.getParentId())) {
                    LOG.warn("Fixing folder.parentId : setting " + folderId + " into cmis:parentId");
                    fso.setParentId(folderId);
                    toBeSaved = true;
                }
                so.setRepositoryId(repositoryId);
            } else {
                so = new DocumentImpl();
                File metadataFile = new File(child.getAbsolutePath()
                        + SUFFIXE_METADATA);
                so.setId(persistenceManager.generateId());

                DocumentImpl meta = (DocumentImpl) persistenceManager
                        .readCMISFromDisk(metadataFile);
                
                if (meta != null) {
                    // if metadata file exists then meta is the CMIS Object
                    so = meta;
                } else {
                    LOG.warn("Missing metadata file for " + child.getAbsolutePath());
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
                    LOG.warn("Fixing document.parentIds : adding " + folderId + " into cmis:parentIds");
                    /*
                     * This is a bug fixing patch, in order to fix the orphean documents by adding
                     * their current physical directory to the list of ParentIds. 
                     * but we can't know if the orphean situation comes from a 'move' or a 'addToFolder' bug
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
                // read contentStream
                ((DocumentImpl) so).setContent(persistenceManager.readContent(child, true));
                so.setRepositoryId(repositoryId);
                so.setStore(store);
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
                loadFolder(repositoryId, store, child, so.getId(), filenameFilter, persistenceManager);
            }
        }
    }

}