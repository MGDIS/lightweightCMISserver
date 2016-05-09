/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.inmemory.server;

import static org.apache.chemistry.opencmis.commons.impl.XMLUtils.next;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.apache.chemistry.opencmis.commons.impl.IOUtils;
import org.apache.chemistry.opencmis.commons.impl.JSONConverter;
import org.apache.chemistry.opencmis.commons.impl.XMLConverter;
import org.apache.chemistry.opencmis.commons.impl.XMLUtils;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.AbstractTypeDefinition;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.BindingsObjectFactoryImpl;
import org.apache.chemistry.opencmis.commons.impl.json.JSONArray;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParser;
import org.apache.chemistry.opencmis.commons.impl.server.AbstractServiceFactory;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.server.CmisService;
import org.apache.chemistry.opencmis.commons.spi.BindingsObjectFactory;
import org.apache.chemistry.opencmis.inmemory.ConfigConstants;
import org.apache.chemistry.opencmis.inmemory.ConfigurationSettings;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoreManager;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.StoreManagerFactory;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.StoreManagerImpl;
import org.apache.chemistry.opencmis.inmemory.types.TypeUtil;
import org.apache.chemistry.opencmis.server.support.CmisServiceWrapper;
import org.apache.chemistry.opencmis.server.support.TypeManager;
import org.apache.chemistry.opencmis.util.repository.ObjectGenerator;
import org.apache.chemistry.opencmis.utils.BasicUserManager;
import org.apache.chemistry.opencmis.utils.FilePersistenceLoader;
import org.apache.chemistry.opencmis.utils.IUserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// suppress Apache chemistry depreciation
@SuppressWarnings("deprecation")
public class InMemoryServiceFactoryImpl extends AbstractServiceFactory {

    private static final Logger LOG = LoggerFactory
            .getLogger(InMemoryServiceFactoryImpl.class.getName());
    private static final BigInteger DEFAULT_MAX_ITEMS_OBJECTS = BigInteger
            .valueOf(1000);
    private static final BigInteger DEFAULT_MAX_ITEMS_TYPES = BigInteger
            .valueOf(100);
    private static final BigInteger DEFAULT_DEPTH_OBJECTS = BigInteger
            .valueOf(2);
    private static final BigInteger DEFAULT_DEPTH_TYPES = BigInteger
            .valueOf(-1);
    private static CallContext overrideCtx;

    private boolean fUseOverrideCtx = false;
    private StoreManager storeManager; // singleton root of everything
    private CleanManager cleanManager = null;

    private File tempDir;
    private int memoryThreshold;
    private long maxContentSize;
    private boolean encrypt;

    private static final String CONFIG_FILENAME = "repository.properties";

    
    public void init(Map<String, String> parameters) {
        LOG.info("Initializing cmis server...");

        // List all repository definition files
        String[] repositories = new String[0];
        try {
            List<String> repos = new ArrayList<String>();
            Enumeration<URL> urls = this.getClass().getClassLoader()
                    .getResources("/");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                // look for classes directory
                if (url.toString().contains("classes")) {
                    File classes = new File(url.toURI());
                    if (classes.isDirectory()) {
                        for (String file : classes.list()) {
                            if (file.endsWith(".properties") && !file.contains(CONFIG_FILENAME) && !file.contains("log4j.properties") )
                                repos.add(file);
                        }
                    }
                }
            }
            repositories = repos.toArray(repositories);
        } catch (IOException e1) {
            LOG.warn("When loading repository definitions", e1);
        } catch (URISyntaxException e) {
            LOG.warn("When parsing URI", e);
        }

        for (String repository : repositories) {
            // load properties
            InputStream stream = this.getClass()
                    .getResourceAsStream("/" + repository);

            Properties props = new Properties();
            try {
                props.load(stream);
            } catch (IOException e) {
                LOG.warn("Cannot load configuration: " + e, e);
            } finally {
                IOUtils.closeQuietly(stream);
            }

            // initialize factory instance
            Map<String, String> repositoryParameters = new HashMap<String, String>();

            for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
                String key = (String) e.nextElement();
                String value = props.getProperty(key);
                repositoryParameters.put(key, value);
            }

            configureRepository(repositoryParameters);
        }

        if (repositories.length == 0) {
            configureRepository(parameters);
        }
        
        LOG.info("...initialized cmis server.");
    }

    // Configure a new Repository from external file properties
    private void configureRepository(Map<String, String> parameters) {
        String repositoryId = parameters.get(ConfigConstants.REPOSITORY_ID);
        LOG.info("Adding " + repositoryId + " repository...");
        LOG.debug("Init paramaters: " + parameters);

        String overrideCtxParam = parameters
                .get(ConfigConstants.OVERRIDE_CALL_CONTEXT);
        if (null != overrideCtxParam) {
            fUseOverrideCtx = true;
        }

        ConfigurationSettings.init(parameters);

        IUserManager userManager = new BasicUserManager();

        if (!repositoryId.equals("UnitTestRepository")) {
            for (String key : parameters.keySet()) {
                if (key.startsWith(ConfigConstants.PREFIX_USER)) {
                    // get logins
                    String usernameAndPassword = parameters.get(key);
                    if (usernameAndPassword == null) {
                        continue;
                    }

                    String username = usernameAndPassword;
                    String password = "";

                    int x = usernameAndPassword.indexOf(':');
                    if (x > -1) {
                        username = usernameAndPassword.substring(0, x);
                        password = usernameAndPassword.substring(x + 1);
                    }

                    LOG.info("Adding login '{}'.", username);

                    userManager.addLogin(username, password);
                }
            }
        }

        String repositoryClassName = parameters
                .get(ConfigConstants.REPOSITORY_CLASS);
        if (null == repositoryClassName) {
            repositoryClassName = StoreManagerImpl.class.getName();
        }

        String tempDirStr = parameters.get(ConfigConstants.TEMP_DIR);
        tempDir = (tempDirStr == null ? super.getTempDirectory() : new File(
                tempDirStr));

        if (null == storeManager) {
            storeManager = StoreManagerFactory
                    .createInstance(repositoryClassName);
        }

        String memoryThresholdStr = parameters
                .get(ConfigConstants.MEMORY_THRESHOLD);
        memoryThreshold = (memoryThresholdStr == null ? super
                .getMemoryThreshold() : Integer.parseInt(memoryThresholdStr));

        String maxContentSizeStr = parameters
                .get(ConfigConstants.MAX_CONTENT_SIZE);
        maxContentSize = (maxContentSizeStr == null ? super.getMaxContentSize()
                : Long.parseLong(maxContentSizeStr));

        String encryptTempFilesStr = parameters
                .get(ConfigConstants.ENCRYPT_TEMP_FILES);
        encrypt = (encryptTempFilesStr == null ? super.encryptTempFiles()
                : Boolean.parseBoolean(encryptTempFilesStr));

        Date deploymentTime = new Date();
        String strDate = new SimpleDateFormat("EEE MMM dd hh:mm:ss a z yyyy",
                Locale.US).format(deploymentTime);

        parameters.put(ConfigConstants.DEPLOYMENT_TIME, strDate);

        boolean created = initStorageManager(parameters);

        if (created) {
            fillRepositoryIfConfigured(parameters);
        }

        Long cleanInterval = ConfigurationSettings
                .getConfigurationValueAsLong(ConfigConstants.CLEAN_REPOSITORY_INTERVAL);
        if (null != cleanInterval && cleanInterval > 0) {
            scheduleCleanRepositoryJob(cleanInterval);
        }

        // Load file system
        if (tempDirStr != null) {
            FilePersistenceLoader.loadDirectory(storeManager, parameters);
        }

        // Add userManager
        storeManager.setUserManager(repositoryId, userManager);
    }

    public static void setOverrideCallContext(CallContext ctx) {
        overrideCtx = ctx;
    }

    
    public CmisService getService(CallContext context) {
        
        // If a repository is specified then authenticate the incoming user
        // Else let request going
        if (context.getRepositoryId() != null
                && !context.getRepositoryId().equals("UnitTestRepository")) {
            if (storeManager.getUserManager(context.getRepositoryId()) != null) {
                // if the authentication fails, authenticate() throws a
                // CmisPermissionDeniedException
                storeManager.getUserManager(context.getRepositoryId())
                        .authenticate(context);
            }
        }

        CallContext contextToUse = context;
        // Attach the CallContext to a thread local context that can be
        // accessed from everywhere
        // Some unit tests set their own context. So if we find one then we use
        // this one and ignore the provided one. Otherwise we set a new context.
        if (fUseOverrideCtx && null != overrideCtx) {
            contextToUse = overrideCtx;
        }

        InMemoryService inMemoryService = InMemoryServiceContext
                .getCmisService();
        if (inMemoryService == null) {
            CmisServiceWrapper<InMemoryService> wrapperService;
            inMemoryService = new InMemoryService(storeManager);
            wrapperService = new CmisServiceWrapper<InMemoryService>(
                    inMemoryService, DEFAULT_MAX_ITEMS_TYPES,
                    DEFAULT_DEPTH_TYPES, DEFAULT_MAX_ITEMS_OBJECTS,
                    DEFAULT_DEPTH_OBJECTS);
            InMemoryServiceContext.setWrapperService(wrapperService);
        }

        inMemoryService.setCallContext(contextToUse);

        return inMemoryService;
    }

    
    public File getTempDirectory() {
        return tempDir;
    }

    
    public boolean encryptTempFiles() {
        return encrypt;
    }

    
    public int getMemoryThreshold() {
        return memoryThreshold;
    }

    
    public long getMaxContentSize() {
        return maxContentSize;
    }

    
    public void destroy() {
        LOG.debug("Destroying InMemory service instance.");
        if (null != cleanManager) {
            cleanManager.stopCleanRepositoryJob();
        }
        InMemoryServiceContext.setWrapperService(null);
    }

    public StoreManager getStoreManger() {
        return storeManager;
    }

    private boolean initStorageManager(Map<String, String> parameters) {
        // initialize in-memory management
        boolean created = false;
        String repositoryClassName = parameters
                .get(ConfigConstants.REPOSITORY_CLASS);
        if (null == repositoryClassName) {
            repositoryClassName = StoreManagerImpl.class.getName();
        }

        if (null == storeManager) {
            storeManager = StoreManagerFactory
                    .createInstance(repositoryClassName);
        }

        String repositoryId = parameters.get(ConfigConstants.REPOSITORY_ID);

        List<String> allAvailableRepositories = storeManager
                .getAllRepositoryIds();

        // init existing repositories
        for (String existingRepId : allAvailableRepositories) {
            storeManager.initRepository(existingRepId, parameters);
        }

        // create repository if configured as a startup parameter
        if (null != repositoryId) {
            if (allAvailableRepositories.contains(repositoryId)) {
                LOG.warn("Repostory " + repositoryId
                        + " already exists and will not be created.");
            } else {
                String typeCreatorClassName = parameters
                        .get(ConfigConstants.TYPE_CREATOR_CLASS);
                // Add repository file path
                storeManager.createAndInitRepository(repositoryId, parameters,
                        typeCreatorClassName);
                created = true;
            }
        }

        // check if a type definitions XML file is configured. if yes import
        // type definitions
        String typeDefsFileName = parameters.get(ConfigConstants.TYPE_FILE);
        if (null == typeDefsFileName) {
            LOG.info("No file name for type definitions given, no types will be created.");
        } else {
            TypeManager typeManager = storeManager.getTypeManager(repositoryId);
            TypeManager tmc = typeManager;
            importTypesFromFile(tmc, typeDefsFileName);
        }
        return created;
    }

    private void importTypesFromFile(TypeManager tmc, String typeDefsFileName) {

        BufferedInputStream stream = null;
        TypeDefinition typeDef = null;
        File f = new File(typeDefsFileName);
        InputStream typesStream = null;

        if (!f.isFile()) {
            LOG.info("Types file " + f.getAbsolutePath() + " does not exist");
            String classpathFile = "/type.xml";
            LOG.info("So looking in " + classpathFile);
            typesStream = this.getClass().getResourceAsStream(classpathFile);
        } else if (f.canRead()) {
            LOG.info("Found Types file " + f.getAbsolutePath());
            try {
                typesStream = new FileInputStream(f);
            } catch (Exception e) {
                LOG.error("Could not load type definitions from file '"
                        + typeDefsFileName + "': " + e);
            }
        }

        if (typesStream == null) {
            LOG.warn("Resource file with type definitions " + typeDefsFileName
                    + " could not be found, no additional CMIS types will be created.");
            return;
        }

       try {
            stream = new BufferedInputStream(typesStream);
            XMLStreamReader parser = XMLUtils.createParser(stream);
            XMLUtils.findNextStartElemenet(parser);

            // walk through all nested tags in top element
            while (true) {
                int event = parser.getEventType();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    QName name = parser.getName();
                    if (name.getLocalPart().equals("type")) {
                        typeDef = XMLConverter.convertTypeDefinition(parser);
                        LOG.debug("New CMIS type : "
                                + typeDef.getLocalName());
                        if (typeDef.getPropertyDefinitions() == null) {
                            ((AbstractTypeDefinition) typeDef)
                                    .setPropertyDefinitions(new LinkedHashMap<String, PropertyDefinition<?>>());
                        }
                        tmc.addTypeDefinition(typeDef, true);
                    }
                    XMLUtils.next(parser);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    break;
                } else {
                    if (!next(parser)) {
                        break;
                    }
                }
            }
            parser.close();
        } catch (Exception e) {
            LOG.error("Could not load type definitions from file '"
                    + typeDefsFileName + "': " + e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private static List<String> readPropertiesToSetFromConfig(
            Map<String, String> parameters, String keyPrefix) {
        List<String> propsToSet = new ArrayList<String>();
        for (int i = 0;; ++i) {
            String propertyKey = keyPrefix + Integer.toString(i);
            String propertyToAdd = parameters.get(propertyKey);
            if (null == propertyToAdd) {
                break;
            } else {
                propsToSet.add(propertyToAdd);
            }
        }
        return propsToSet;
    }

    private void fillRepositoryIfConfigured(Map<String, String> parameters) {

        class DummyCallContext implements CallContext {

            
            public String get(String key) {
                return null;
            }

            
            public String getBinding() {
                return null;
            }

            
            public boolean isObjectInfoRequired() {
                return false;
            }

            
            public CmisVersion getCmisVersion() {
                return CmisVersion.CMIS_1_1;
            }

            
            public String getRepositoryId() {
                return null;
            }

            
            public String getLocale() {
                return null;
            }

            
            public BigInteger getOffset() {
                return null;
            }

            
            public BigInteger getLength() {
                return null;
            }

            
            public String getPassword() {
                return null;
            }

            
            public String getUsername() {
                return null;
            }

            
            public File getTempDirectory() {

                return tempDir;
            }

            
            public boolean encryptTempFiles() {
                return encrypt;
            }

            
            public int getMemoryThreshold() {
                return memoryThreshold;
            }

            
            public long getMaxContentSize() {
                return maxContentSize;
            }
        }

        String repositoryId = parameters.get(ConfigConstants.REPOSITORY_ID);
        String doFillRepositoryStr = parameters
                .get(ConfigConstants.USE_REPOSITORY_FILER);
        String contentKindStr = parameters.get(ConfigConstants.CONTENT_KIND);
        boolean doFillRepository = doFillRepositoryStr == null ? false
                : Boolean.parseBoolean(doFillRepositoryStr);

        if (doFillRepository) {

            // create an initial temporary service instance to fill the
            // repository

            InMemoryService svc = new InMemoryService(storeManager);

            BindingsObjectFactory objectFactory = new BindingsObjectFactoryImpl();

            String levelsStr = parameters.get(ConfigConstants.FILLER_DEPTH);
            int levels = 1;
            if (null != levelsStr) {
                levels = Integer.parseInt(levelsStr);
            }

            String docsPerLevelStr = parameters
                    .get(ConfigConstants.FILLER_DOCS_PER_FOLDER);
            int docsPerLevel = 1;
            if (null != docsPerLevelStr) {
                docsPerLevel = Integer.parseInt(docsPerLevelStr);
            }

            String childrenPerLevelStr = parameters
                    .get(ConfigConstants.FILLER_FOLDERS_PER_FOLDER);
            int childrenPerLevel = 2;
            if (null != childrenPerLevelStr) {
                childrenPerLevel = Integer.parseInt(childrenPerLevelStr);
            }

            String documentTypeId = parameters
                    .get(ConfigConstants.FILLER_DOCUMENT_TYPE_ID);
            if (null == documentTypeId) {
                documentTypeId = BaseTypeId.CMIS_DOCUMENT.value();
            }

            String folderTypeId = parameters
                    .get(ConfigConstants.FILLER_FOLDER_TYPE_ID);
            if (null == folderTypeId) {
                folderTypeId = BaseTypeId.CMIS_FOLDER.value();
            }

            int contentSizeKB = 0;
            String contentSizeKBStr = parameters
                    .get(ConfigConstants.FILLER_CONTENT_SIZE);
            if (null != contentSizeKBStr) {
                contentSizeKB = Integer.parseInt(contentSizeKBStr);
            }

            ObjectGenerator.ContentKind contentKind;
            if (null == contentKindStr) {
                contentKind = ObjectGenerator.ContentKind.LOREM_IPSUM_TEXT;
            } else {
                if (contentKindStr.equals("static/text")) {
                    contentKind = ObjectGenerator.ContentKind.STATIC_TEXT;
                } else if (contentKindStr.equals("lorem/text")) {
                    contentKind = ObjectGenerator.ContentKind.LOREM_IPSUM_TEXT;
                } else if (contentKindStr.equals("lorem/html")) {
                    contentKind = ObjectGenerator.ContentKind.LOREM_IPSUM_HTML;
                } else if (contentKindStr.equals("fractal/jpeg")) {
                    contentKind = ObjectGenerator.ContentKind.IMAGE_FRACTAL_JPEG;
                } else {
                    contentKind = ObjectGenerator.ContentKind.STATIC_TEXT;
                }
            }
            // Create a hierarchy of folders and fill it with some documents
            ObjectGenerator gen = new ObjectGenerator(objectFactory, svc, svc,
                    svc, repositoryId, contentKind);

            gen.setNumberOfDocumentsToCreatePerFolder(docsPerLevel);

            // Set the type id for all created documents:
            gen.setDocumentTypeId(documentTypeId);

            // Set the type id for all created folders:
            gen.setFolderTypeId(folderTypeId);

            // Set contentSize
            gen.setContentSizeInKB(contentSizeKB);

            // set properties that need to be filled
            // set the properties the generator should fill with values for
            // documents:
            // Note: must be valid properties in configured document and folder
            // type

            List<String> propsToSet = readPropertiesToSetFromConfig(parameters,
                    ConfigConstants.FILLER_DOCUMENT_PROPERTY);
            if (null != propsToSet) {
                gen.setDocumentPropertiesToGenerate(propsToSet);
            }

            propsToSet = readPropertiesToSetFromConfig(parameters,
                    ConfigConstants.FILLER_FOLDER_PROPERTY);
            if (null != propsToSet) {
                gen.setFolderPropertiesToGenerate(propsToSet);
            }

            // Simulate a runtime context with configuration parameters
            // Attach the CallContext to a thread local context that can be
            // accessed from everywhere
            DummyCallContext ctx = new DummyCallContext();
            // create thread local storage and attach call context
            getService(ctx);

            // Build the tree
            RepositoryInfo rep = svc.getRepositoryInfo(repositoryId, null);
            String rootFolderId = rep.getRootFolderId();

            try {
                gen.createFolderHierachy(levels, childrenPerLevel, rootFolderId);
                // Dump the tree
                gen.dumpFolder(rootFolderId, "*");
            } catch (Exception e) {
                LOG.error("Could not create folder hierarchy with documents. ",
                        e);
            }
            destroy();
        } // if

    } // fillRepositoryIfConfigured

    class CleanManager {

        private final ScheduledExecutorService scheduler = Executors
                .newScheduledThreadPool(1);
        private ScheduledFuture<?> cleanerHandle = null;

        public void startCleanRepositoryJob(long intervalInMinutes) {

            final Runnable cleaner = new Runnable() {
                
                public void run() {
                    LOG.info("Cleaning repository as part of a scheduled maintenance job.");
                    for (String repositoryId : storeManager
                            .getAllRepositoryIds()) {
                        ObjectStore store = storeManager
                                .getObjectStore(repositoryId);
                        store.clear();
                        fillRepositoryIfConfigured(ConfigurationSettings
                                .getParameters());
                    }
                    LOG.info("Repository cleaned. Freeing memory.");
                    System.gc();
                }
            };

            LOG.info("Repository Clean Job starting clean job, interval "
                    + intervalInMinutes + " min");
            cleanerHandle = scheduler.scheduleAtFixedRate(cleaner,
                    intervalInMinutes, intervalInMinutes, TimeUnit.MINUTES);
        }

        public void stopCleanRepositoryJob() {
            LOG.info("Repository Clean Job cancelling clean job.");
            boolean ok = cleanerHandle.cancel(true);
            LOG.info("Repository Clean Job cancelled with result: " + ok);
            scheduler.shutdownNow();
        }
    }

    private void scheduleCleanRepositoryJob(long minutes) {
        cleanManager = new CleanManager();
        cleanManager.startCleanRepositoryJob(minutes);
    }

}
