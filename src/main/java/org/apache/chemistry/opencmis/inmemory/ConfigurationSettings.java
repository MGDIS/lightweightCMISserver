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
package org.apache.chemistry.opencmis.inmemory;

import java.util.Map;

import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigurationSettings {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationSettings.class.getName());

    private static ConfigurationSettings singleInstance;

    private final Map<String, String> parameters;

    private ConfigurationSettings(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    private String getConfigurationValueIntern(String key) {
        return parameters.get(key);
    }

    public static String getConfigurationValueAsString(String key) {
        if (null == singleInstance) {
            LOG.error("ConfigurationSettings are not initialized. Use default values");
            switch (key) {
                case "InMemoryServer.MaxContentSizeKB":
                    return 4096 + "";
                case "InMemoryServer.RepositoryId":
                    return "default";
                case "InMemoryServer.RepositoryName":
                    return "Default Repository";
                case "InMemoryServer.RepositoryDescription":
                    return "The lightweight file system CMIS server";
                case "InMemoryServer.RepositoryThinClientUri":
                    return "http://localhost:8080/lightweightcmis/browser/root/default";
                case "InMemoryServer.TypeDefinitionsFile":
                    return "/data/cmis/default-types.xml";
                case "InMemoryServer.Class":
                    return "org.apache.chemistry.opencmis.inmemory.storedobj.impl.StoreManagerImpl";
                case "InMemoryServer.MemoryThreshold":
                    return "10485760";
                case "InMemoryServer.MaxContentSize":
                    return "20971520";
                case "InMemoryServer.EncryptTempFiles":
                    return "false";
                case "InMemoryServer.DeploymentTime":
                case "InMemoryServer.CleanIntervalMinutes":
                case "InMemoryServer.RepositoryInfoCreatorClass":
                case "InMemoryServer.OverrideCallContext":
                case "InMemoryServer.TypesCreatorClass":
                    return null;

                case "RepositoryFiller.Enable":
                    return "false";
                case "RepositoryFiller.DocumentTypeId":
                    return "cmis:document";
                case "RepositoryFiller.FolderTypeId":
                    return "cmis:folder";
                case "RepositoryFiller.DocsPerFolder":
                    return "3";
                case "RepositoryFiller.FolderPerFolder":
                    return "2";
                case "RepositoryFiller.Depth":
                    return "3";
                case "RepositoryFiller.ContentSizeInKB":
                    return "32";
                case "RepositoryFiller.ContentKind":
                    return "lorem/text";

                case "persistenceDirectory":
                    return "/data/cmis/default";
                default:
                    break;
            }
        }
        return singleInstance.getConfigurationValueIntern(key);
    }

    public static Long getConfigurationValueAsLong(String key) {
        String str = getConfigurationValueAsString(key);
        if (null != str) {
            return Long.valueOf(str);
        } else {
            return null;
        }
    }

    public static void init(Map<String, String> parameters) {
        singleInstance = new ConfigurationSettings(parameters);
    }

    public static Map<String, String> getParameters() {
        return singleInstance.parameters;
    }
}
