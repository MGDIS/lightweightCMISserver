package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.inmemory.ConfigConstants;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.MultiFiling;
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

    public static void loadDirectory(StoreManager storeManager,
            Map<String, String> parameters) {
        String repositoryId = parameters.get(ConfigConstants.REPOSITORY_ID);
        ObjectStore store = storeManager.getObjectStore(repositoryId);
        // get the folder
        File folder = new File(parameters.get(ConfigConstants.TEMP_DIR));
        if (!folder.isDirectory()) {
            throw new CmisObjectNotFoundException(folder.getAbsolutePath()
                    + " is not a folder!");
        }

        FilenameFilter filenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !name.endsWith(SUFFIXE_METADATA);
            }
        };
        loadFolder(repositoryId, store, folder, filenameFilter, storeManager
                .getObjectStore(repositoryId).getPersistenceManager());
    }

    private static void loadFolder(String repositoryId, ObjectStore store,
            File folder, FilenameFilter filenameFilter,
            IPersistenceManager persistenceManager) {
        // iterate through children
        for (File child : folder.listFiles(filenameFilter)) {
            // skip hidden files
            if (child.isHidden()) {
                continue;
            }

            StoredObject so = null;
            if (child.isDirectory()) {
                so = new FolderImpl(child.getName(), child.getParent());
                so.setName(child.getName());
                so.setId(persistenceManager.getId(child));
                StoredObject meta = persistenceManager
                        .readCMISFromDisk(new File(child.getAbsolutePath()
                                + SUFFIXE_METADATA));
                if (meta != null) {
                    so = meta;
                } else {
                    ((Folder) so).setParentId(persistenceManager.getId(folder));
                    so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                    so.setTypeId(BaseTypeId.CMIS_FOLDER.value());
                }
                so.setRepositoryId(repositoryId);
                // recursive loading
                loadFolder(repositoryId, store, child, filenameFilter,
                        persistenceManager);
            } else {
                so = new DocumentImpl();
                so.setName(child.getName());
                so.setId(persistenceManager.getId(child));
                StoredObject meta = persistenceManager
                        .readCMISFromDisk(new File(child.getAbsolutePath()
                                + SUFFIXE_METADATA));
                if (meta != null) {
                    so = meta;
                } else {
                    ((MultiFiling) so).addParentId(persistenceManager
                            .getId(folder));
                    so.setProperties(new LinkedHashMap<String, PropertyData<?>>());
                    so.setTypeId(BaseTypeId.CMIS_DOCUMENT.value());
                }
                // no content in memory
                //((DocumentImpl) so).setContent(persistenceManager
                //        .readContent(child));
                so.setRepositoryId(repositoryId);
                so.setStore(store);
            }
            LOG.debug("Loading file " + so.getName());
            store.storeObject(so, false);
        }
    }

}