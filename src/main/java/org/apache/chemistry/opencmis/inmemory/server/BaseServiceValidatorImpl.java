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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.CreatablePropertyTypes;
import org.apache.chemistry.opencmis.commons.data.ExtensionsData;
import org.apache.chemistry.opencmis.commons.data.NewTypeSettableAttributes;
import org.apache.chemistry.opencmis.commons.data.Properties;
import org.apache.chemistry.opencmis.commons.data.RepositoryCapabilities;
import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.definitions.Choice;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDecimalDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyIntegerDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyStringDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionContainer;
import org.apache.chemistry.opencmis.commons.enums.AclPropagation;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.chemistry.opencmis.commons.enums.RelationshipDirection;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConstraintException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.spi.Holder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.CmisServiceValidator;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Policy;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoreManager;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;

public class BaseServiceValidatorImpl implements CmisServiceValidator {

    protected static final String UNKNOWN_OBJECT_ID = "Unknown object id: ";
    protected static final String UNKNOWN_REPOSITORY_ID = "Unknown repository id: ";
    protected static final String OBJECT_ID_CANNOT_BE_NULL = "Object Id cannot be null.";
    protected static final String REPOSITORY_ID_CANNOT_BE_NULL = "Repository Id cannot be null.";
    protected static final String UNKNOWN_TYPE_ID = "Unknown type id: ";
    protected static final String TYPE_ID_CANNOT_BE_NULL = "Type Id cannot be null.";
    protected final StoreManager fStoreManager;

    public BaseServiceValidatorImpl(StoreManager sm) {
        fStoreManager = sm;
    }

    /**
     * Check if repository is known and that object exists. To avoid later calls
     * to again retrieve the object from the id return the retrieved object for
     * later use.
     * 
     * @param repositoryId
     *            repository id
     * @param objectId
     *            object id
     * @param action
     *            cmis action
     * @return object for objectId
     */
    protected StoredObject checkStandardParameters(String repositoryId, String objectId, String action) {
        // consider idempotency for many deleteTree
    	if (!"deleteTree".equals(action)) {
    		return checkStandardParameters(repositoryId, objectId);
    	}
        if (null == repositoryId) {
            throw new CmisInvalidArgumentException(REPOSITORY_ID_CANNOT_BE_NULL);
        }

        if (null == objectId) {
            throw new CmisInvalidArgumentException(OBJECT_ID_CANNOT_BE_NULL);
        }

        ObjectStore objStore = fStoreManager.getObjectStore(repositoryId);

        if (objStore == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_REPOSITORY_ID + repositoryId);
        }

        return objStore.getObjectById(objectId);
    }
    
    /**
     * Check if repository is known and that object exists. To avoid later calls
     * to again retrieve the object from the id return the retrieved object for
     * later use.
     * 
     * @param repositoryId
     *            repository id
     * @param objectId
     *            object id
     * @return object for objectId
     */
    protected StoredObject checkStandardParameters(String repositoryId, String objectId) {
        if (null == repositoryId) {
            throw new CmisInvalidArgumentException(REPOSITORY_ID_CANNOT_BE_NULL);
        }

        if (null == objectId) {
            throw new CmisInvalidArgumentException(OBJECT_ID_CANNOT_BE_NULL);
        }

        ObjectStore objStore = fStoreManager.getObjectStore(repositoryId);

        if (objStore == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_REPOSITORY_ID + repositoryId);
        }

        StoredObject so = objStore.getObjectById(objectId);

        if (so == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_OBJECT_ID + objectId);
        }

        return so;
    }

    protected StoredObject checkStandardParametersByPath(String repositoryId, String path, String user, String action) {
    	// consider idempotency for many deleteTree
    	if (!"deleteTree".equals(action)) {
    		return checkStandardParametersByPath(repositoryId, path, user);
    	}
        if (null == repositoryId) {
            throw new CmisInvalidArgumentException(REPOSITORY_ID_CANNOT_BE_NULL);
        }

        if (null == path) {
            throw new CmisInvalidArgumentException("Path parameter cannot be null.");
        }

        ObjectStore objStore = fStoreManager.getObjectStore(repositoryId);

        if (objStore == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_REPOSITORY_ID + repositoryId);
        }

        return objStore.getObjectByPath(path, user);
    }
    
    protected StoredObject checkStandardParametersByPath(String repositoryId, String path, String user) {
        if (null == repositoryId) {
            throw new CmisInvalidArgumentException(REPOSITORY_ID_CANNOT_BE_NULL);
        }

        if (null == path) {
            throw new CmisInvalidArgumentException("Path parameter cannot be null.");
        }

        ObjectStore objStore = fStoreManager.getObjectStore(repositoryId);

        if (objStore == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_REPOSITORY_ID + repositoryId);
        }

        StoredObject so = objStore.getObjectByPath(path, user);

        if (so == null) {
            throw new CmisObjectNotFoundException("Unknown path: " + path);
        }

        return so;
    }

    protected StoredObject checkStandardParametersAllowNull(String repositoryId, String objectId) {

        StoredObject so = null;

        if (null == repositoryId) {
            throw new CmisInvalidArgumentException(REPOSITORY_ID_CANNOT_BE_NULL);
        }

        if (null != objectId) {

            ObjectStore objStore = fStoreManager.getObjectStore(repositoryId);

            if (objStore == null) {
                throw new CmisObjectNotFoundException(UNKNOWN_REPOSITORY_ID + repositoryId);
            }

            so = objStore.getObjectById(objectId);

            if (so == null) {
                throw new CmisObjectNotFoundException(UNKNOWN_OBJECT_ID + objectId);
            }
        }

        return so;
    }

    protected StoredObject checkExistingObjectId(ObjectStore objStore, String objectId) {

        if (null == objectId) {
            throw new CmisInvalidArgumentException(OBJECT_ID_CANNOT_BE_NULL);
        }

        StoredObject so = objStore.getObjectById(objectId);

        if (so == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_OBJECT_ID + objectId);
        }

        return so;
    }

    protected void checkRepositoryId(String repositoryId) {
        if (null == repositoryId) {
            throw new CmisInvalidArgumentException(REPOSITORY_ID_CANNOT_BE_NULL);
        }

        ObjectStore objStore = fStoreManager.getObjectStore(repositoryId);

        if (objStore == null) {
            throw new CmisInvalidArgumentException(UNKNOWN_REPOSITORY_ID + repositoryId);
        }
    }

    protected StoredObject[] checkParams(String repositoryId, String objectId1, String objectId2) {
        StoredObject[] so = new StoredObject[2];
        checkRepositoryId(repositoryId);
        ObjectStore objectStore = fStoreManager.getObjectStore(repositoryId);
        so[0] = checkExistingObjectId(objectStore, objectId1);
        so[1] = checkExistingObjectId(objectStore, objectId2);
        return so;
    }

    protected void checkPolicies(String repositoryId, List<String> policyIds) {
        if (policyIds != null && policyIds.size() > 0) {
            for (String policyId : policyIds) {
                TypeDefinitionContainer tdc = fStoreManager.getTypeById(repositoryId, policyId);
                if (tdc == null) {
                    throw new CmisInvalidArgumentException("Unknown policy type: " + policyId);
                }
                if (tdc.getTypeDefinition().getBaseTypeId() != BaseTypeId.CMIS_POLICY) {
                    throw new CmisInvalidArgumentException(policyId + " is not a policy type");
                }
            }
        }
    }

    protected void checkCreatablePropertyTypes(String repositoryId,
            Collection<PropertyDefinition<?>> propertyDefinitions )
    {
        RepositoryInfo repositoryInfo = fStoreManager.getRepositoryInfo(repositoryId);
        RepositoryCapabilities repositoryCapabilities = repositoryInfo.getCapabilities();
        CreatablePropertyTypes creatablePropertyTypes = repositoryCapabilities.getCreatablePropertyTypes();
        
        Set<PropertyType> creatablePropertyTypeSet = creatablePropertyTypes.canCreate();
        for (PropertyDefinition<?> propertyDefinition : propertyDefinitions)
        {
            if (!creatablePropertyTypeSet.contains(propertyDefinition.getPropertyType()))
                throw new CmisConstraintException("propertyDefinition " + propertyDefinition.getId() +
                        "is of not creatable type " + propertyDefinition.getPropertyType());
            
            // mandatory properties must have a default value
            if (propertyDefinition.isRequired() && 
                    (propertyDefinition.getDefaultValue() == null))
            {
                throw new CmisConstraintException("property: " + propertyDefinition.getId() + 
                        "required properties must have a default value");
            }
        }
    }
    
    protected void checkSettableAttributes(String repositoryId, TypeDefinition oldTypeDefinition,
            TypeDefinition newTypeDefinition )
    {
        RepositoryInfo repositoryInfo = fStoreManager.getRepositoryInfo(repositoryId);
        RepositoryCapabilities repositoryCapabilities = repositoryInfo.getCapabilities();
        NewTypeSettableAttributes newTypeSettableAttributes = repositoryCapabilities.getNewTypeSettableAttributes();
        
        if (null == newTypeSettableAttributes)
            return; // no restrictions defined
        if (newTypeSettableAttributes.canSetControllableAcl() &&
                newTypeSettableAttributes.canSetControllablePolicy() &&
                newTypeSettableAttributes.canSetCreatable() &&
                newTypeSettableAttributes.canSetDescription() &&
                newTypeSettableAttributes.canSetDisplayName() &&
                newTypeSettableAttributes.canSetFileable() &&
                newTypeSettableAttributes.canSetFulltextIndexed() &&
                newTypeSettableAttributes.canSetId() &&
                newTypeSettableAttributes.canSetIncludedInSupertypeQuery() &&
                newTypeSettableAttributes.canSetLocalName() &&
                newTypeSettableAttributes.canSetLocalNamespace() &&
                newTypeSettableAttributes.canSetQueryable() &&
                newTypeSettableAttributes.canSetQueryName())
            return;  // all is allowed
        if (!newTypeSettableAttributes.canSetControllableAcl() && 
                oldTypeDefinition.isControllableAcl() != newTypeDefinition.isControllableAcl())
            throw new CmisConstraintException("controllableAcl is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in controllableAcl");
        if (!newTypeSettableAttributes.canSetControllablePolicy() && 
                oldTypeDefinition.isControllablePolicy() != newTypeDefinition.isControllablePolicy())
            throw new CmisConstraintException("controllablePolicy is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in controllablePolicy");
        if (!newTypeSettableAttributes.canSetCreatable() && 
                oldTypeDefinition.isCreatable() != newTypeDefinition.isCreatable())
            throw new CmisConstraintException("isCreatable is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in isCreatable");
        if (!newTypeSettableAttributes.canSetDescription() && 
                oldTypeDefinition.getDescription() != newTypeDefinition.getDescription())
            throw new CmisConstraintException("description is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their description");
        if (!newTypeSettableAttributes.canSetDisplayName() && 
                oldTypeDefinition.getDisplayName() != newTypeDefinition.getDisplayName())
            throw new CmisConstraintException("displayName is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their displayName");
        if (!newTypeSettableAttributes.canSetFileable() && 
                oldTypeDefinition.isFileable() != newTypeDefinition.isFileable())
            throw new CmisConstraintException("fileable is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in isFileable");
        if (!newTypeSettableAttributes.canSetFulltextIndexed() && 
                oldTypeDefinition.isFulltextIndexed() != newTypeDefinition.isFulltextIndexed())
            throw new CmisConstraintException("fulltextIndexed is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in isFulltextIndexed");
        // TODO  how can the ids differ?
        if (!newTypeSettableAttributes.canSetId() && 
                oldTypeDefinition.getId() != newTypeDefinition.getId())
            throw new CmisConstraintException("id is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their id");
        if (!newTypeSettableAttributes.canSetIncludedInSupertypeQuery() && 
                oldTypeDefinition.isIncludedInSupertypeQuery() != newTypeDefinition.isIncludedInSupertypeQuery())
            throw new CmisConstraintException("includedInSupertypeQuery is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their isIncludedInSupertypeQuery");
        if (!newTypeSettableAttributes.canSetLocalName() && 
                oldTypeDefinition.getLocalName() != newTypeDefinition.getLocalName())
            throw new CmisConstraintException("localName is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their localName");
        if (!newTypeSettableAttributes.canSetLocalNamespace() && 
                oldTypeDefinition.getLocalNamespace() != newTypeDefinition.getLocalNamespace())
            throw new CmisConstraintException("localNamespace is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their localNamespace");
        if (!newTypeSettableAttributes.canSetQueryable() && 
                oldTypeDefinition.isQueryable() != newTypeDefinition.isQueryable())
            throw new CmisConstraintException("queryable is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their isQueryable");
        if (!newTypeSettableAttributes.canSetQueryName() && 
                oldTypeDefinition.getQueryName() != newTypeDefinition.getQueryName())
            throw new CmisConstraintException("queryName is not settable in repository " + repositoryId + ", but " +
                     oldTypeDefinition.getId() + " and " + newTypeDefinition.getId() + " differ in their queryName");
    }

    protected void checkUpdatePropertyDefinitions(Map<String,PropertyDefinition<?>> oldPropertyDefinitions,
            Map<String,PropertyDefinition<?>> newPropertyDefinitions)
    {
        for(PropertyDefinition<?> newPropertyDefinition : newPropertyDefinitions.values())
        {                   
            PropertyDefinition<?> oldPropertyDefinition = oldPropertyDefinitions.get(newPropertyDefinition.getId());
        
            if (oldPropertyDefinition.isInherited())
                throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                        " update of inherited properties is not allowed");
            if (!(oldPropertyDefinition.isRequired()) && newPropertyDefinition.isRequired())
                throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                        " optional properties must not be changed to required");    
            if (oldPropertyDefinition.getPropertyType() != newPropertyDefinition.getPropertyType())
                throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                        " cannot update the propertyType (" + oldPropertyDefinition.getPropertyType() + ")");   
            if (oldPropertyDefinition.getCardinality() != newPropertyDefinition.getCardinality())
                throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                        " cannot update the cardinality (" + oldPropertyDefinition.getCardinality() + ")"); 
            
            if (oldPropertyDefinition.isOpenChoice() && !newPropertyDefinition.isOpenChoice() )
                throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                        " open choice cannot change from true to false");
            
            // check choices
            if (!oldPropertyDefinition.isOpenChoice())
            {
                List<?> oldChoices = oldPropertyDefinition.getChoices();
                if (null == oldChoices)
                    throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                            " there should be any choices when it's no open choice");
                List<?> newChoices = newPropertyDefinition.getChoices();
                if (null == newChoices)
                throw new CmisConstraintException("property: " + newPropertyDefinition.getId() + 
                        " there should be any choices when it's no open choice");
                ListIterator<?> newChoicesIterator = newChoices.listIterator();
                for (Object oldChoiceObject : oldChoices)
                {
                    Object newChoiceObject = newChoicesIterator.next();
                    if (!(oldChoiceObject instanceof Choice))
                        throw new CmisConstraintException("property: " + newPropertyDefinition.getId() + 
                                " old choice object is not of class Choice: " + oldChoiceObject.toString());
                    if (!(newChoiceObject instanceof Choice))
                        throw new CmisConstraintException("property: " + newPropertyDefinition.getId() + 
                                " new choice object is not of class Choice: " + newChoiceObject.toString());
                    Choice<?> oldChoice = (Choice<?>) oldChoiceObject;
                    Choice<?> newChoice = (Choice<?>) newChoiceObject;
                    List<?> oldValues = oldChoice.getValue();
                    List<?> newValues = newChoice.getValue();
                    for (Object oldValue : oldValues)
                    {                       
                        if (! newValues.contains(oldValue))
                            throw new CmisConstraintException("property: " + newPropertyDefinition.getId() + 
                                    " value: " + oldValue.toString() + " is not in new values of the new choice");
                    }       
                }   
            }
        
            // check restrictions
            if (oldPropertyDefinition instanceof PropertyDecimalDefinition)
            {
                PropertyDecimalDefinition oldPropertyDecimalDefinition = (PropertyDecimalDefinition) oldPropertyDefinition;
                PropertyDecimalDefinition newPropertyDecimalDefinition = (PropertyDecimalDefinition) newPropertyDefinition;
                
                BigDecimal oldMinValue = oldPropertyDecimalDefinition.getMinValue();
                BigDecimal newMinValue = newPropertyDecimalDefinition.getMinValue();
                if (null != newMinValue &&
                        (oldMinValue == null || (newMinValue.compareTo( oldMinValue) > 0)))
                    throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                            " minValue " + oldMinValue + " cannot be further restricted to " + newMinValue);
                
                BigDecimal oldMaxValue = oldPropertyDecimalDefinition.getMaxValue();
                BigDecimal newMaxValue = newPropertyDecimalDefinition.getMaxValue();
                if (null != newMaxValue &&
                        (oldMaxValue == null || (newMaxValue.compareTo( oldMaxValue) < 0)))
                    throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                            " maxValue " + oldMaxValue + " cannot be further restricted to " + newMaxValue);
            }
            if (oldPropertyDefinition instanceof PropertyIntegerDefinition)
            {
                PropertyIntegerDefinition oldPropertyIntegerDefinition = (PropertyIntegerDefinition) oldPropertyDefinition;
                PropertyIntegerDefinition newPropertyIntegerDefinition = (PropertyIntegerDefinition) newPropertyDefinition;
                
                BigInteger oldMinValue = oldPropertyIntegerDefinition.getMinValue();
                BigInteger newMinValue = newPropertyIntegerDefinition.getMinValue();
                if (null != newMinValue &&
                        (oldMinValue == null || (newMinValue.compareTo( oldMinValue) > 0)))
                    throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                            " minValue " + oldMinValue + " cannot be further restricted to " + newMinValue);
                
                BigInteger oldMaxValue = oldPropertyIntegerDefinition.getMaxValue();
                BigInteger newMaxValue = newPropertyIntegerDefinition.getMaxValue();
                if (null != newMaxValue &&
                        (oldMaxValue == null || (newMaxValue.compareTo( oldMaxValue) < 0)))
                    throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                            " maxValue " + oldMaxValue + " cannot be further restricted to " + newMaxValue);
            }
            if (oldPropertyDefinition instanceof PropertyStringDefinition)
            {
                PropertyStringDefinition oldPropertyStringDefinition = (PropertyStringDefinition) oldPropertyDefinition;
                PropertyStringDefinition newPropertyStringDefinition = (PropertyStringDefinition) newPropertyDefinition;
                
                BigInteger oldMaxValue = oldPropertyStringDefinition.getMaxLength();
                BigInteger newMaxValue = newPropertyStringDefinition.getMaxLength();
                if (null != newMaxValue &&
                        (oldMaxValue == null || (newMaxValue.compareTo( oldMaxValue) < 0)))
                    throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                            " maxValue " + oldMaxValue + " cannot be further restricted to " + newMaxValue);
            }
        }
        
        // check for removed properties
        for(PropertyDefinition<?> oldPropertyDefinition : oldPropertyDefinitions.values())
        {
            PropertyDefinition<?> newPropertyDefinition = newPropertyDefinitions.get(oldPropertyDefinition.getId());
            if (null == newPropertyDefinition)
            {
                throw new CmisConstraintException("property: " + oldPropertyDefinition.getId() + 
                        " cannot remove that property");    
            }
        }
    }
    
    protected void checkUpdateType (TypeDefinition updateType, TypeDefinition type)
    {
        if (updateType.getId() != type.getId())
            throw new CmisConstraintException("type to update must be of the same id: " + updateType.getId() + ", " + type.getId());
        if (updateType.getBaseTypeId() != type.getBaseTypeId())
            throw new CmisConstraintException("base type to update must be the same: " + updateType.getBaseTypeId() + ", " + type.getBaseTypeId());
        // anything else should be ignored          
    }
    
    protected TypeDefinition checkExistingTypeId(String repositoryId, String typeId) {

        if (null == typeId) {
            throw new CmisInvalidArgumentException(TYPE_ID_CANNOT_BE_NULL);
        }

        TypeDefinitionContainer tdc = fStoreManager.getTypeById(repositoryId, typeId);
        if (tdc == null) {
            throw new CmisObjectNotFoundException(UNKNOWN_TYPE_ID + typeId);
        }

        return tdc.getTypeDefinition();
    }


    protected void checkBasicType(TypeDefinition type)
    {
        if (type.getId() == type.getBaseTypeId().value())       
            throw new CmisInvalidArgumentException("type " + type.getId() + " is a basic type, basic types are read-only");  
    }
    
    
    public void getRepositoryInfos(CallContext context, ExtensionsData extension) {
    }

    
    public void getRepositoryInfo(CallContext context, String repositoryId, ExtensionsData extension) {

        checkRepositoryId(repositoryId);
    }

    
    public void getTypeChildren(CallContext context, String repositoryId, String typeId, ExtensionsData extension) {

        checkRepositoryId(repositoryId);
    }

    
    public void getTypeDescendants(CallContext context, String repositoryId, String typeId, ExtensionsData extension) {

        checkRepositoryId(repositoryId);
    }

    
    public void getTypeDefinition(CallContext context, String repositoryId, String typeId, ExtensionsData extension) {

        checkRepositoryId(repositoryId);
    }

    
    public StoredObject getChildren(CallContext context, String repositoryId, String folderId, 
                ExtensionsData extension) {

        return checkStandardParameters(repositoryId, folderId);
    }

    
    public StoredObject getDescendants(CallContext context, String repositoryId, String folderId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, folderId);
    }

    
    public StoredObject getFolderTree(CallContext context, String repositoryId, String folderId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, folderId);
    }

    
    public StoredObject getObjectParents(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject getFolderParent(CallContext context, String repositoryId, String folderId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, folderId);
    }

    
    public StoredObject getCheckedOutDocs(CallContext context, String repositoryId, String folderId,
            ExtensionsData extension) {

        if (null != folderId) {
            return checkStandardParameters(repositoryId, folderId);
        } else {
            checkRepositoryId(repositoryId);
            return null;
        }

    }

    
    public StoredObject createDocument(CallContext context, String repositoryId, String folderId,
            List<String> policyIds, ExtensionsData extension) {
        return checkStandardParametersAllowNull(repositoryId, folderId);
    }

    
    public StoredObject createDocumentFromSource(CallContext context, String repositoryId, String sourceId,
            String folderId, List<String> policyIds, ExtensionsData extension) {

        return checkStandardParametersAllowNull(repositoryId, sourceId);
    }

    
    public StoredObject createFolder(CallContext context, String repositoryId, String folderId, List<String> policyIds,
            ExtensionsData extension) {
        return checkStandardParameters(repositoryId, folderId);
    }

    
    public StoredObject[] createRelationship(CallContext context, String repositoryId, String sourceId,
            String targetId, List<String> policyIds, ExtensionsData extension) {
        checkRepositoryId(repositoryId);
        checkStandardParametersAllowNull(repositoryId, null);
        return checkParams(repositoryId, sourceId, targetId);
    }

    
    public StoredObject createPolicy(CallContext context, String repositoryId, String folderId, Acl addAces,
            Acl removeAces, List<String> policyIds, ExtensionsData extension) {

        return checkStandardParametersAllowNull(repositoryId, folderId);
    }

    // CMIS 1.1
    
    public StoredObject createItem(CallContext context, String repositoryId, Properties properties, String folderId,
            List<String> policies, Acl addAces, Acl removeAces, ExtensionsData extension) {
        return checkStandardParametersAllowNull(repositoryId, folderId);
    }

    
    public StoredObject getAllowableActions(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {
        //
        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject getObject(CallContext context, String repositoryId, String objectId, ExtensionsData extension) {

        StoredObject so = checkStandardParameters(repositoryId, objectId);
        return so;
    }

    
    public StoredObject getProperties(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject getRenditions(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject getObjectByPath(CallContext context, String repositoryId, String path, 
            ExtensionsData extension) {
    	Boolean isDeleteTree = false;
    	if(context.get("httpServletRequest") != null) {
	    	String qs = ((HttpServletRequest) context.get("httpServletRequest")).getQueryString();
	    	isDeleteTree = qs != null && qs.contains("cmisaction=deleteTree");
    	}
        return checkStandardParametersByPath(repositoryId, path, context.getUsername(), isDeleteTree ? "deleteTree" : null);
    }

    
    public StoredObject getContentStream(CallContext context, String repositoryId, String objectId, String streamId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject updateProperties(CallContext context, String repositoryId, Holder<String> objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId.getValue());
    }

    
    public StoredObject[] moveObject(CallContext context, String repositoryId, Holder<String> objectId,
            String targetFolderId, String sourceFolderId, ExtensionsData extension) {

        StoredObject[] res = new StoredObject[3];
        res[0] = checkStandardParameters(repositoryId, objectId.getValue());
        res[1] = checkExistingObjectId(fStoreManager.getObjectStore(repositoryId), sourceFolderId);
        res[2] = checkExistingObjectId(fStoreManager.getObjectStore(repositoryId), targetFolderId);
        return res;
    }

    
    public StoredObject deleteObject(CallContext context, String repositoryId, String objectId, Boolean allVersions,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject deleteTree(CallContext context, String repositoryId, String folderId, Boolean allVersions,
            UnfileObject unfileObjects, ExtensionsData extension) {
        return checkStandardParameters(repositoryId, folderId, "deleteTree");
    }

    
    public StoredObject setContentStream(CallContext context, String repositoryId, Holder<String> objectId,
            Boolean overwriteFlag, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId.getValue());
    }

    
    public StoredObject appendContentStream(CallContext context, String repositoryId, Holder<String> objectId,
            ExtensionsData extension) {
        return checkStandardParameters(repositoryId, objectId.getValue());
    }

    
    public StoredObject deleteContentStream(CallContext context, String repositoryId, Holder<String> objectId,
            ExtensionsData extension) {
        return checkStandardParameters(repositoryId, objectId.getValue());
    }

    
    public StoredObject checkOut(CallContext context, String repositoryId, Holder<String> objectId,
            ExtensionsData extension, Holder<Boolean> contentCopied) {

        return checkStandardParameters(repositoryId, objectId.getValue());
    }

    
    public StoredObject cancelCheckOut(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject checkIn(CallContext context, String repositoryId, Holder<String> objectId, Acl addAces,
            Acl removeAces, List<String> policyIds, ExtensionsData extension) {
        return checkStandardParameters(repositoryId, objectId.getValue());
    }

    
    public StoredObject getObjectOfLatestVersion(CallContext context, String repositoryId, String objectId,
            String versionSeriesId, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, versionSeriesId == null ? objectId : versionSeriesId);
    }

    
    public StoredObject getPropertiesOfLatestVersion(CallContext context, String repositoryId, String objectId,
            String versionSeriesId, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, versionSeriesId == null ? objectId : versionSeriesId);
    }

    
    public StoredObject getAllVersions(CallContext context, String repositoryId, String objectId,
            String versionSeriesId, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, versionSeriesId == null ? objectId : versionSeriesId);
    }

    
    public void query(CallContext context, String repositoryId, ExtensionsData extension) {

        checkRepositoryId(repositoryId);
    }

    
    public void getContentChanges(CallContext context, String repositoryId, ExtensionsData extension) {

        checkRepositoryId(repositoryId);
    }

    
    public StoredObject[] addObjectToFolder(CallContext context, String repositoryId, String objectId, String folderId,
            Boolean allVersions, ExtensionsData extension) {

        return checkParams(repositoryId, objectId, folderId);
    }

    
    public StoredObject[] removeObjectFromFolder(CallContext context, String repositoryId, String objectId,
            String folderId, ExtensionsData extension) {

        if (folderId != null) {
            return checkParams(repositoryId, objectId, folderId);
        } else {
            StoredObject[] so = new StoredObject[1];
            checkRepositoryId(repositoryId);
            ObjectStore objectStore = fStoreManager.getObjectStore(repositoryId);
            so[0] = checkExistingObjectId(objectStore, objectId);
            return so;
        }
    }

    
    public StoredObject getObjectRelationships(CallContext context, String repositoryId, String objectId,
            RelationshipDirection relationshipDirection, String typeId, ExtensionsData extension) {

        StoredObject so = checkStandardParameters(repositoryId, objectId);

        if (relationshipDirection == null) {
            throw new CmisInvalidArgumentException("Relationship direction cannot be null.");
        }

        if (typeId != null) {
            TypeDefinition typeDef = fStoreManager.getTypeById(repositoryId, typeId).getTypeDefinition();
            if (typeDef == null) {
                throw new CmisInvalidArgumentException("Type Id " + typeId + " is not known in repository "
                        + repositoryId);
            }

            if (!typeDef.getBaseTypeId().equals(BaseTypeId.CMIS_RELATIONSHIP)) {
                throw new CmisInvalidArgumentException("Type Id " + typeId + " is not a relationship type.");
            }
        }
        return so;
    }

    
    public StoredObject getAcl(CallContext context, String repositoryId, String objectId, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject applyAcl(CallContext context, String repositoryId, String objectId,
            AclPropagation aclPropagation, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject[] applyPolicy(CallContext context, String repositoryId, String policyId, String objectId,
            ExtensionsData extension) {

        return checkParams(repositoryId, policyId, objectId);
    }

    
    public StoredObject[] removePolicy(CallContext context, String repositoryId, String policyId, String objectId,
            ExtensionsData extension) {

        StoredObject[] sos = checkParams(repositoryId, policyId, objectId);
        StoredObject pol = sos[0];
        if (!(pol instanceof Policy)) {
            throw new CmisInvalidArgumentException("Id " + policyId + " is not a policy object.");
        }
        return sos;
    }

    
    public StoredObject getAppliedPolicies(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject create(CallContext context, String repositoryId, String folderId, ExtensionsData extension) {

        return checkStandardParameters(repositoryId, folderId);
    }

    public StoredObject deleteObjectOrCancelCheckOut(CallContext context, String repositoryId, String objectId,
            ExtensionsData extension) {

        return checkStandardParameters(repositoryId, objectId);
    }

    
    public StoredObject applyAcl(CallContext context, String repositoryId, String objectId) {

        return checkStandardParameters(repositoryId, objectId);
    }
    
    
    public void createType(CallContext callContext, String repositoryId, TypeDefinition type, ExtensionsData extension) {
        checkRepositoryId(repositoryId);

        if (null == type) {
            throw new CmisInvalidArgumentException("Type cannot be null.");
        }
        String parentTypeId = type.getParentTypeId();
        TypeDefinitionContainer parentTypeContainer = fStoreManager.getTypeById(repositoryId, parentTypeId);
        if (null == parentTypeContainer) {
            throw new CmisInvalidArgumentException(UNKNOWN_TYPE_ID + parentTypeId);
        }
        TypeDefinition parentType = parentTypeContainer.getTypeDefinition();
        // check if type can be created
        if (!(parentType.getTypeMutability().canCreate())) {
            throw new CmisConstraintException("parent type: " + parentTypeId + " does not allow mutability create");
        }
        checkCreatablePropertyTypes(repositoryId, type.getPropertyDefinitions().values());
    }

    
    public TypeDefinition updateType(CallContext callContext,
            String repositoryId, TypeDefinition type, ExtensionsData extension) {
        checkRepositoryId(repositoryId);
        
        TypeDefinition updateType = checkExistingTypeId(repositoryId, type.getId());
        checkUpdateType(updateType, type);
        checkBasicType(type);
        // check if type can be updated
        if (!(updateType.getTypeMutability().canUpdate())) {
            throw new CmisConstraintException("type: " + type.getId() + " does not allow mutability update");
        }
        checkCreatablePropertyTypes(repositoryId, type.getPropertyDefinitions().values());
        checkSettableAttributes(repositoryId, updateType, type );
        checkUpdatePropertyDefinitions(updateType.getPropertyDefinitions(), type.getPropertyDefinitions());
        return updateType;
    }

    
    public TypeDefinition deleteType(CallContext callContext, String repositoryId,
            String typeId, ExtensionsData extension) {
        checkRepositoryId(repositoryId);
        
        TypeDefinition deleteType =  checkExistingTypeId(repositoryId, typeId);
        checkBasicType(deleteType);
        // check if type can be deleted
        if (!(deleteType.getTypeMutability().canDelete())) {
            throw new CmisConstraintException("type: " + typeId + " does not allow mutability delete");
        }
        return deleteType;
    }
    
}
