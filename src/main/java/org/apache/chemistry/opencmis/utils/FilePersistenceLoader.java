package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.AbstractMap.SimpleImmutableEntry;

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
        
        LOG.info("Scanning " + folder.getAbsolutePath());
        
        ExecutorService pool = Executors.newFixedThreadPool(100);
        FolderProcessor folderProcessor = new FolderProcessor(pool);
        folderProcessor.loadFolder(repositoryId, store, folder, manager.getRootId(), filenameFilter, manager);

        LOG.info("... End Scanning");
    }
}