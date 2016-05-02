package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.Constants;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertiesImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyStringImpl;
import org.apache.chemistry.opencmis.inmemory.ConfigConstants;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.MultiFiling;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoreManager;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.DocumentImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.FolderImpl;
import org.apache.chemistry.opencmis.server.support.query.CmisQlExtParser_CmisBaseGrammar.boolean_factor_return;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.org.apache.bcel.internal.generic.ISTORE;

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
        // get the folder
        File folder = new File(parameters.get(ConfigConstants.TEMP_DIR));
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
    }

    private static void loadFolder(String repositoryId, ObjectStore store,
            File folder, String folderId, FilenameFilter filenameFilter,
            PersistenceManager persistenceManager) {
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
                // check if cmis:path exists
                if (metadataFile.exists()) {
                    try {
                        String metadata = FileUtils.readFileToString(metadataFile);
                        if (!metadata.contains("cmis:path")) {
                            toBeSaved = true;
                        }
                    } catch (IOException e) {}
                }
                
                FolderImpl meta = (FolderImpl) persistenceManager
                        .readCMISFromDisk(metadataFile);
                if (meta != null) {
                    // if metadata file exists then meta is the CMIS Object
                    so = meta;
                } else {
                    so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                    so.setTypeId(BaseTypeId.CMIS_FOLDER.value());
                    so.setName(child.getName());
                    toBeSaved = true;
                }
                so.getProperties().put(PropertyIds.PATH, new PropertyStringImpl(PropertyIds.PATH, relativePath));
                Folder fso = (Folder) so;
                if (fso.getTypeId() == null) fso.setTypeId("cmis:folder");
                if (fso.getParentId() == null) fso.setParentId(folderId);
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
                    so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                    so.setName(child.getName());
                    so.setTypeId(BaseTypeId.CMIS_DOCUMENT.value());
                    toBeSaved = true;
                }
                DocumentImpl dso = (DocumentImpl) so;
                if(dso.getTypeId() == null) dso.setTypeId("cmis:document");
                if (dso.getParentIds() == null || dso.getParentIds().size() == 0) dso.addParentId(folderId);
                // read contentStream
                ((DocumentImpl) so).setContent(persistenceManager.readContent(child, true));
                so.setRepositoryId(repositoryId);
                so.setStore(store);
            }
            // save metadata to disk
            if (toBeSaved) {
                LOG.warn("Missing metadata file, save metadata to disk " + child.getAbsolutePath() + SUFFIXE_METADATA);
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