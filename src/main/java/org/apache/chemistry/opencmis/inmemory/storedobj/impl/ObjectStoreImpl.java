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
package org.apache.chemistry.opencmis.inmemory.storedobj.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.Ace;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.data.RenditionData;
import org.apache.chemistry.opencmis.commons.enums.AclPropagation;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.enums.RelationshipDirection;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConstraintException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNameConstraintViolationException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStorageException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyStringImpl;
import org.apache.chemistry.opencmis.inmemory.ConfigConstants;
import org.apache.chemistry.opencmis.inmemory.ConfigurationSettings;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Content;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Document;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.DocumentVersion;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Fileable;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Filing;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.MultiFiling;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Relationship;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.VersionedDocument;
import org.apache.chemistry.opencmis.inmemory.types.DefaultTypeSystemCreator;
import org.apache.chemistry.opencmis.utils.PersistenceManager;
import org.apache.chemistry.opencmis.utils.InMemoryPersistence;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The object store is the central core of the in-memory repository. It is based
 * on huge HashMap map mapping ids to objects in memory. To allow access from
 * multiple threads a Java concurrent HashMap is used that allows parallel
 * access methods.
 * <p>
 * Certain methods in the in-memory repository must guarantee constraints. For
 * example a folder enforces that each child has a unique name. Therefore
 * certain operations must occur in an atomic manner. In the example it must be
 * guaranteed that no write access occurs to the map between acquiring the
 * iterator to find the children and finishing the add operation when no name
 * conflicts can occur. For this purpose this class has methods to lock an
 * unlock the state of the repository. It is very important that the caller
 * acquiring the lock enforces an unlock under all circumstances. Typical code
 * is:
 * <p>
 * 
 * <pre>
 * ObjectStoreImpl os = ... ;
 * try {
 *     os.lock();
 * } finally {
 *     os.unlock();
 * }
 * </pre>
 * 
 * The locking is very coarse-grained. Productive implementations would probably
 * implement finer grained locks on a folder or document rather than the
 * complete repository.
 */
public class ObjectStoreImpl implements ObjectStore {

	private static final Logger LOG = LoggerFactory
			.getLogger(ObjectStoreImpl.class.getName());
	private static final int FIRST_ID = 100;
	private static final Long MAX_CONTENT_SIZE_KB = ConfigurationSettings
			.getConfigurationValueAsLong(ConfigConstants.MAX_CONTENT_SIZE_KB);

	/**
	 * User id for administrator always having all rights.
	 */
	public static final String ADMIN_PRINCIPAL_ID = "Admin";

	/**
	 * Simple id generator that uses just an integer.
	 */
	private static int nextUnusedId = FIRST_ID;

	/**
	 * A concurrent HashMap as core element to hold all objects in the
	 * repository.
	 */
	private final Map<String, StoredObject> fStoredObjectMap = new ConcurrentHashMap<String, StoredObject>();

	/**
	 * A concurrent HashMap to hold all Acls in the repository.
	 */
	private int nextUnusedAclId = 0;

	private final List<InMemoryAcl> fAcls = new ArrayList<InMemoryAcl>();

	private final Lock fLock = new ReentrantLock();

	private final String fRepositoryId;
	private FolderImpl fRootFolder = null;

	private final String repositoryFilePath;
	private PersistenceManager persistenceManager = new InMemoryPersistence();

	public ObjectStoreImpl(String repositoryId, String filePath,
			PersistenceManager persistenceManager) {
		fRepositoryId = repositoryId;
		repositoryFilePath = filePath;
		if (persistenceManager != null) {
			this.persistenceManager = persistenceManager;
			this.persistenceManager.setRootPath(filePath);
		}
		createRootFolder();
	}

	private static synchronized Integer getNextId() {
		return nextUnusedId++;
	}

	private synchronized Integer getNextAclId() {
		return nextUnusedAclId++;
	}

	private void lock() {
		fLock.lock();
	}

	private void unlock() {
		fLock.unlock();
	}

	public Folder getRootFolder() {
		return fRootFolder;
	}

	public StoredObject getObjectByPath(String path, String user) {
		if (path == "/") {
			return fRootFolder;
		}
		StoredObject so = fStoredObjectMap.get(path);
		return so;
	}

	private Fileable findObjectWithPathInDescendents(String path, String user,
			String prefix, Fileable fo) {
		if (path.equals(prefix)) {
			return fo;
		} else if (fo instanceof Folder) {
			List<Fileable> children = getChildren((Folder) fo);
			for (Fileable child : children) {
				String foundPath = prefix.length() == 1 ? prefix
						+ child.getName() : prefix + Filing.PATH_SEPARATOR
						+ child.getName();
				if (path.startsWith(foundPath)) {
					Fileable found = findObjectWithPathInDescendents(path,
							user, foundPath, child);
					if (null != found) {
						return found; // note that there can be multiple folders
										// with the same prefix like folder1,
										// folder10
					}
				}
			}
		}
		return null;
	}

	public StoredObject getObjectById(String objectId) {
		// we use path as id so we just can look it up in the map
		StoredObject so = fStoredObjectMap.get(objectId);
		return so;
	}

	public void deleteObject(String objectId, Boolean allVersions, String user) {
		StoredObject obj = fStoredObjectMap.get(objectId);

		if (null == obj) {
			throw new CmisObjectNotFoundException(
					"Cannot delete object with id  " + objectId
							+ ". Object does not exist.");
		}

		if (obj instanceof FolderImpl) {
			deleteFolder(objectId, user);
		} else if (obj instanceof DocumentVersion) {
			DocumentVersion vers = (DocumentVersion) obj;
			VersionedDocument parentDoc = vers.getParentDocument();
			boolean otherVersionsExists;
			if (allVersions != null && allVersions) {
				otherVersionsExists = false;
				List<DocumentVersion> allVers = parentDoc.getAllVersions();
				for (DocumentVersion ver : allVers) {
					removeObject(ver.getId());
				}
			} else {
				removeObject(objectId);
				otherVersionsExists = parentDoc.deleteVersion(vers);
				persistenceManager.deleteFromDisk(vers);
			}

			if (!otherVersionsExists) {
				removeObject(parentDoc.getId());
			}
		} else {
			removeObject(objectId);
		}
	}

	public String storeObject(StoredObject so) {
		return storeObject(so, true);
	}

	public String storeObject(StoredObject so, boolean saveOnExit) {
		String id = so.getId();
		// check if update or create
		if (null == id) {
			File newFile = persistenceManager.calculateFile(fStoredObjectMap,
					so);
			if (newFile != null) {
				id = persistenceManager.getId(newFile);
			}
			// Keep old behavior
			if (id == null) {
				id = getNextId().toString();
			}
			if (so instanceof Folder && !id.equals(persistenceManager.getRootId())) {
				id = Long.toString(System.nanoTime(), 36);
			}
			so.setId(id);
		}

		if (saveOnExit) {
			persistenceManager.saveObject(fStoredObjectMap, so, true);
		}
		// put by id
		fStoredObjectMap.put(id, so);
		// put by path
		if (so instanceof Fileable) {
			String path = ((Fileable) so).getPath();
			if (path != null) {
				fStoredObjectMap.put(path, so);
			}
		}

		return id;
	}

	StoredObject getObject(String id) {
		return fStoredObjectMap.get(id);
	}

	void removeObject(String id) {
		StoredObject obj = fStoredObjectMap.get(id);
		if (obj instanceof Fileable) {
			// remove path entry
			fStoredObjectMap.remove(((Fileable) obj).getPath());
		}
		// remove id entry
		fStoredObjectMap.remove(id);
		persistenceManager.deleteFromDisk(obj);
		LOG.debug("Deleted " + obj.getName());
	}

	public Set<String> getIds() {
		Set<String> entries = fStoredObjectMap.keySet();
		return entries;
	}

	/**
	 * Clear repository and remove all data.
	 */

	public void clear() {
		lock();
		fStoredObjectMap.clear();
		storeObject(fRootFolder);
		unlock();
	}

	public long getObjectCount() {
		return fStoredObjectMap.size();
	}

	// /////////////////////////////////////////
	// private helper methods

	private void createRootFolder() {
		FolderImpl rootFolder = new FolderImpl();
		rootFolder.setName(repositoryFilePath);
		rootFolder.setParentId(null);
		rootFolder.setTypeId(BaseTypeId.CMIS_FOLDER.value());
		rootFolder.setCreatedBy("Admin");
		rootFolder.setModifiedBy("Admin");
		rootFolder.setModifiedAtNow();
		rootFolder.setRepositoryId(fRepositoryId);
		rootFolder.setAclId(addAcl(InMemoryAcl.getDefaultAcl()));
		// Do not save content when it is a folder
		String id = storeObject(rootFolder, false);
		rootFolder.setId(id);
		fRootFolder = rootFolder;
	}

	public Document createDocument(Map<String, PropertyData<?>> propMap,
			String user, Folder folder, ContentStream contentStream,
			List<String> policies, Acl addACEs, Acl removeACEs) {
		String name = (String) propMap.get(PropertyIds.NAME).getFirstValue();
		DocumentImpl doc = new DocumentImpl();
		doc.setStore(this);
		doc.createSystemBasePropertiesWhenCreated(propMap, user);
		doc.setCustomProperties(propMap);
		doc.setRepositoryId(fRepositoryId);
		doc.setName(name);
		if (null != folder) {
			 // allow same multiple name in folder
			 if (hasChild(folder, name)) {
				 String message = "Cannot create document an object with name "
						 + name + " already exists in folder " + getFolderPath(folder.getId());
				 LOG.error(message);
				 throw new CmisNameConstraintViolationException(message);
			 }
			doc.addParentId(folder.getId());
		}
		ContentStream content = setContent(doc, contentStream);
		doc.setContent(content);
		int aclId = getAclId(((FolderImpl) folder), addACEs, removeACEs);
		doc.setAclId(aclId);
		if (null != policies) {
			doc.setAppliedPolicies(policies);
		}
		String id = storeObject(doc);
		doc.setId(id);
		applyAcl(doc, addACEs, removeACEs);
		return doc;
	}

	public StoredObject createItem(String name,
			Map<String, PropertyData<?>> propMap, String user, Folder folder,
			List<String> policies, Acl addACEs, Acl removeACEs) {
		ItemImpl item = new ItemImpl();
		item.createSystemBasePropertiesWhenCreated(propMap, user);
		item.setCustomProperties(propMap);
		item.setRepositoryId(fRepositoryId);
		item.setName(name);
		if (null != folder) {
			if (hasChild(folder, name)) {
				throw new CmisNameConstraintViolationException(
						"Cannot create document an object with name " + name
								+ " already exists in folder "
								+ getFolderPath(folder.getId()));
			}
			item.addParentId(folder.getId());
		}
		if (null != policies) {
			item.setAppliedPolicies(policies);
		}
		int aclId = getAclId(((FolderImpl) folder), addACEs, removeACEs);
		item.setAclId(aclId);
		String id = storeObject(item);
		item.setId(id);
		applyAcl(item, addACEs, removeACEs);
		return item;
	}

	public DocumentVersion createVersionedDocument(String name,
			Map<String, PropertyData<?>> propMap, String user, Folder folder,
			List<String> policies, Acl addACEs, Acl removeACEs,
			ContentStream contentStream, VersioningState versioningState) {
		VersionedDocumentImpl doc = new VersionedDocumentImpl();
		doc.createSystemBasePropertiesWhenCreated(propMap, user);
		doc.setCustomProperties(propMap);
		doc.setRepositoryId(fRepositoryId);
		doc.setStore(this);
		doc.setName(name);
		if (null != folder) {
			if (hasChild(folder, name)) {
				throw new CmisNameConstraintViolationException(
						"Cannot create document an object with name " + name
								+ " already exists in folder "
								+ getFolderPath(folder.getId()));
			}
			doc.addParentId(folder.getId());
		}
		String id = storeObject(doc);
		doc.setId(id);
		DocumentVersion version = doc.addVersion(versioningState, user);
		ContentStream content = setContent(version, contentStream);
		version.setContent(content);
		version.createSystemBasePropertiesWhenCreated(propMap, user);
		version.setCustomProperties(propMap);
		int aclId = getAclId(((FolderImpl) folder), addACEs, removeACEs);
		doc.setAclId(aclId);
		if (null != policies) {
			doc.setAppliedPolicies(policies);
		}
		id = storeObject(version);
		version.setId(id);
		applyAcl(doc, addACEs, removeACEs);
		return version;
	}

	public Folder createFolder(String name,
			Map<String, PropertyData<?>> propMap, String user, Folder parent,
			List<String> policies, Acl addACEs, Acl removeACEs) {

		if (null == parent) {
			throw new CmisInvalidArgumentException("Cannot create root folder.");
		}
		else if (hasChild(parent, name)) {
		    // do not return a 409.
		    // return 200
		    String relativePath = persistenceManager.getFile(parent, fStoredObjectMap).getAbsolutePath().replace(persistenceManager.getRootPath(), "");
		    return (FolderImpl)getObjectByPath(relativePath + "/" + name, user);
		    /*
			throw new CmisNameConstraintViolationException(
			 "Cannot create folder, this name already exists in parent folder.");
			 */
		}
		FolderImpl folder = new FolderImpl(name, parent.getId());
		if (null != propMap) {
			folder.createSystemBasePropertiesWhenCreated(propMap, user);
			folder.setCustomProperties(propMap);
		}
		folder.setRepositoryId(fRepositoryId);
		folder.setStore(this);
		int aclId = getAclId(((FolderImpl) parent), addACEs, removeACEs);
		folder.setAclId(aclId);
		if (null != policies) {
			folder.setAppliedPolicies(policies);
		}
		
		if (persistenceManager.getRootPath() != null) {
		    String relativePath = persistenceManager.getFile(parent, fStoredObjectMap).getAbsolutePath().replace(persistenceManager.getRootPath(), "");
		    folder.getProperties().put(PropertyIds.PATH, new PropertyStringImpl(PropertyIds.PATH, relativePath + "/" + folder.getName()));
	    }
		String id = storeObject(folder);
		folder.setId(id);
		applyAcl(folder, addACEs, removeACEs);
		return folder;
	}

	public Folder createFolder(String name) {
		Folder folder = new FolderImpl(name, null);
		folder.setRepositoryId(fRepositoryId);
		return folder;
	}

	public StoredObject createPolicy(String name, String policyText,
			Map<String, PropertyData<?>> propMap, String user, Acl addACEs,
			Acl removeACEs) {
		PolicyImpl policy = new PolicyImpl();
		policy.createSystemBasePropertiesWhenCreated(propMap, user);
		policy.setCustomProperties(propMap);
		policy.setRepositoryId(fRepositoryId);
		policy.setName(name);
		policy.setPolicyText(policyText);
		String id = storeObject(policy);
		policy.setId(id);
		applyAcl(policy, addACEs, removeACEs);
		return policy;
	}

	public StoredObject createRelationship(String name,
			StoredObject sourceObject, StoredObject targetObject,
			Map<String, PropertyData<?>> propMap, String user, Acl addACEs,
			Acl removeACEs) {

		RelationshipImpl rel = new RelationshipImpl();
		rel.createSystemBasePropertiesWhenCreated(propMap, user);
		rel.setCustomProperties(propMap);
		rel.setRepositoryId(fRepositoryId);
		rel.setName(name);
		if (null != sourceObject) {
			rel.setSource(sourceObject.getId());
		}
		if (null != targetObject) {
			rel.setTarget(targetObject.getId());
		}
		String id = storeObject(rel);
		rel.setId(id);
		applyAcl(rel, addACEs, removeACEs);
		return rel;
	}

	public void storeVersion(DocumentVersion version) {
		String id = storeObject(version);
		version.setId(id);
	}

	public void deleteVersion(DocumentVersion version) {
		StoredObject found = fStoredObjectMap.remove(version.getId());

		if (null == found) {
			throw new CmisInvalidArgumentException(
					"Cannot delete object with id  " + version.getId()
							+ ". Object does not exist.");
		}
	}

	public void updateObject(StoredObject so,
			Map<String, PropertyData<?>> newProperties, String user) {
		// nothing to do
		Map<String, PropertyData<?>> properties = so.getProperties();
		for (String key : newProperties.keySet()) {
			PropertyData<?> value = newProperties.get(key);

			if (key.equals(PropertyIds.SECONDARY_OBJECT_TYPE_IDS)) {
				properties.put(key, value); // preserve it even if it is empty!
			} else if (null == value || value.getValues() == null
					|| value.getFirstValue() == null) {
				// delete property
				properties.remove(key);
			} else {
				properties.put(key, value);
			}
		}
		// update system properties and secondary object type ids
		so.updateSystemBasePropertiesWhenModified(properties, user);
		properties.remove(PropertyIds.SECONDARY_OBJECT_TYPE_IDS);
		// Save object metadata but not its content
		persistenceManager.saveObject(fStoredObjectMap, so, false);
	}

	public List<StoredObject> getCheckedOutDocuments(String orderBy,
			String user, IncludeRelationships includeRelationships) {
		List<StoredObject> res = new ArrayList<StoredObject>();

		for (StoredObject so : fStoredObjectMap.values()) {
			if (so instanceof VersionedDocument) {
				VersionedDocument verDoc = (VersionedDocument) so;
				if (verDoc.isCheckedOut() && hasReadAccess(user, verDoc)) {
					res.add(verDoc.getPwc());
				}
			}
		}

		return res;
	}

	public List<StoredObject> getRelationships(String objectId,
			List<String> typeIds, RelationshipDirection direction) {

		List<StoredObject> res = new ArrayList<StoredObject>();

		if (typeIds != null && typeIds.size() > 0) {
			for (String typeId : typeIds) {
				for (StoredObject so : fStoredObjectMap.values()) {
					if (so instanceof Relationship
							&& so.getTypeId().equals(typeId)) {
						Relationship ro = (Relationship) so;
						if (ro.getSourceObjectId().equals(objectId)
								&& (RelationshipDirection.EITHER == direction || RelationshipDirection.SOURCE == direction)) {
							res.add(so);
						} else if (ro.getTargetObjectId().equals(objectId)
								&& (RelationshipDirection.EITHER == direction || RelationshipDirection.TARGET == direction)) {
							res.add(so);
						}
					}
				}
			}
		} else {
			res = getAllRelationships(objectId, direction);
		}
		return res;
	}

	public String getFolderPath(String folderId) {
		StringBuilder sb = new StringBuilder();
		insertPathSegment(sb, folderId);
		return sb.toString();
	}

	private void insertPathSegment(StringBuilder sb, String folderId) {
		Folder folder = (Folder) getObjectById(folderId);
		if (null == folder.getParentId()) {
			if (sb.length() == 0) {
				sb.insert(0, Filing.PATH_SEPARATOR);
			}
		} else {
			sb.insert(0, folder.getName());
			sb.insert(0, Filing.PATH_SEPARATOR);
			insertPathSegment(sb, folder.getParentId());
		}
	}

	public Acl applyAcl(StoredObject so, Acl addAces, Acl removeAces,
			AclPropagation aclPropagation, String principalId) {
		if (aclPropagation == AclPropagation.OBJECTONLY
				|| !(so instanceof Folder)) {
			return applyAcl(so, addAces, removeAces);
		} else {
			return applyAclRecursive(((Folder) so), addAces, removeAces,
					principalId);
		}
	}

	public Acl applyAcl(StoredObject so, Acl acl,
			AclPropagation aclPropagation, String principalId) {
		if (aclPropagation == AclPropagation.OBJECTONLY
				|| !(so instanceof Folder)) {
			return applyAcl(so, acl);
		} else {
			return applyAclRecursive(((Folder) so), acl, principalId);
		}
	}

	public List<Integer> getAllAclsForUser(String principalId,
			Permission permission) {
		List<Integer> acls = new ArrayList<Integer>();
		for (InMemoryAcl acl : fAcls) {
			if (acl.hasPermission(principalId, permission)) {
				acls.add(acl.getId());
			}
		}
		return acls;
	}

	public Acl getAcl(int aclId) {
		InMemoryAcl acl = getInMemoryAcl(aclId);
		return acl == null ? InMemoryAcl.getDefaultAcl().toCommonsAcl() : acl
				.toCommonsAcl();
	}

	public int getAclId(StoredObjectImpl so, Acl addACEs, Acl removeACEs) {
		InMemoryAcl newAcl;
		boolean removeDefaultAcl = false;
		int aclId = 0;

		if (so == null) {
			newAcl = new InMemoryAcl();
		} else {
			aclId = so.getAclId();
			newAcl = getInMemoryAcl(aclId);
			if (null == newAcl) {
				newAcl = new InMemoryAcl();
			} else {
				// copy list so that we can safely change it without effecting
				// the original
				newAcl = new InMemoryAcl(newAcl.getAces());
			}
		}

		if (newAcl.size() == 0 && addACEs == null && removeACEs == null) {
			return 0;
		}

		if (null != removeACEs) {
			for (Ace ace : removeACEs.getAces()) {
				InMemoryAce inMemAce = new InMemoryAce(ace);
				if (inMemAce.equals(InMemoryAce.getDefaultAce())) {
					removeDefaultAcl = true;
				}
			}
		}

		if (so != null && 0 == aclId && !removeDefaultAcl) {
			return 0; // if object grants full access to everyone and it will
						// not be removed we do nothing
		}

		// add ACEs
		if (null != addACEs) {
			for (Ace ace : addACEs.getAces()) {
				InMemoryAce inMemAce = new InMemoryAce(ace);
				if (inMemAce.equals(InMemoryAce.getDefaultAce())) {
					return 0; // if everyone has full access there is no need to
				}
				// add additional ACLs.
				newAcl.addAce(inMemAce);
			}
		}

		// remove ACEs
		if (null != removeACEs) {
			for (Ace ace : removeACEs.getAces()) {
				InMemoryAce inMemAce = new InMemoryAce(ace);
				newAcl.removeAce(inMemAce);
			}
		}

		if (newAcl.size() > 0) {
			return addAcl(newAcl);
		} else {
			return 0;
		}
	}

	private void deleteFolder(String folderId, String user) {
		StoredObject folder = fStoredObjectMap.get(folderId);
		if (folder == null) {
			throw new CmisInvalidArgumentException("Unknown object with id:  "
					+ folderId);
		}

		if (!(folder instanceof FolderImpl)) {
			throw new CmisInvalidArgumentException(
					"Cannot delete folder with id:  " + folderId
							+ ". Object exists but is not a folder.");
		}

		// check if children exist
		List<Fileable> children = getChildren((Folder) folder, -1, -1, user,
				true).getChildren();
		if (children != null && !children.isEmpty()) {
			throw new CmisConstraintException("Cannot delete folder with id:  "
					+ folderId + ". Folder is not empty.");
		}

		// remove by path
		fStoredObjectMap.remove(((FolderImpl) folder).getPath());
		// remove by id
		fStoredObjectMap.remove(folderId);
		// Delete on disk
		persistenceManager.deleteFromDisk(folder);
	}

	public ChildrenResult getChildren(Folder folder, int maxItemsParam,
			int skipCountParam, String user, boolean usePwc) {
		List<Fileable> children = getChildren(folder, user, usePwc);
		sortFolderList(children);

		int maxItems = maxItemsParam < 0 ? children.size() : maxItemsParam;
		int skipCount = skipCountParam < 0 ? 0 : skipCountParam;

		int from = Math.min(skipCount, children.size());
		int to = Math.min(maxItems + from, children.size());
		int noItems = children.size();

		children = children.subList(from, to);
		return new ChildrenResult(children, noItems);
	}

	private List<Fileable> getChildren(Folder folder) {
		return getChildren(folder, null, false);
	}

	private List<Fileable> getChildren(Folder folder, String user,
			boolean usePwc) {
		List<Fileable> children = new ArrayList<Fileable>();
		for (String id : getIds()) {
			StoredObject obj = getObject(id);
			if (obj instanceof Fileable) {
				Fileable pathObj = (Fileable) obj;
				if ((null == user || hasReadAccess(user, obj))
						&& pathObj.getParentIds().contains(folder.getId())) {
					if (pathObj instanceof VersionedDocument) {
						DocumentVersion ver;
						if (usePwc) {
							ver = ((VersionedDocument) pathObj).getPwc();
							if (null == ver) {
								ver = ((VersionedDocument) pathObj)
										.getLatestVersion(false);
							}
						} else {
							ver = ((VersionedDocument) pathObj)
									.getLatestVersion(false);
						}
						children.add(ver);
					} else if (!(pathObj instanceof DocumentVersion)) { // ignore
																		// DocumentVersion
						children.add(pathObj);
					}

				}
			}
		}
		// keep only distinct children ()
		Set<Fileable> uniques = new HashSet<Fileable>(children);
		return (List<Fileable>)uniques.stream()
                .collect(Collectors.toList());
	}

	public ChildrenResult getFolderChildren(Folder folder, int maxItems,
			int skipCount, String user) {
		List<Fileable> folderChildren = new ArrayList<Fileable>();
		for (String id : getIds()) {
			StoredObject obj = getObject(id);
			if (hasReadAccess(user, obj) && obj instanceof Folder) {
				Folder childFolder = (Folder) obj;
				if (childFolder.getParentIds().contains(folder.getId())) {
					folderChildren.add(childFolder);
				}
			}
		}
		sortFolderList(folderChildren);
		int from = Math.min(skipCount, folderChildren.size());
		int to = Math.min(maxItems + from, folderChildren.size());
		int noItems = folderChildren.size();

		folderChildren = folderChildren.subList(from, to);

		return new ChildrenResult(folderChildren, noItems);
	}

	public void move(StoredObject so, Folder oldParent, Folder newParent,
			String user) {
		if (hasChild(newParent, so.getName())) {
			throw new CmisInvalidArgumentException("Cannot move object "
					+ so.getName() + " to folder "
					+ getFolderPath(newParent.getId())
					+ ". A child with this name already exists.");
		}
		try {
			lock();
			if (so instanceof MultiFiling) {
				MultiFiling fi = (MultiFiling) so;
				// physical move
				this.persistenceManager.moveObject(this.fStoredObjectMap, so, newParent);
				addParentIntern(fi, newParent);
				removeParentIntern(fi, oldParent);
			} else if (so instanceof FolderImpl) {
				// remove MapEntry with old path
				fStoredObjectMap.remove(((FolderImpl) so).getPath());
				((FolderImpl) so).setParentId(newParent.getId());
				fStoredObjectMap.put(((FolderImpl) so).getPath(), so);
			}
		} catch (IOException e) {
			LOG.error("Could not move object", e);
		} finally {
			unlock();
		}
	}

	public void rename(StoredObject so, String newName, String user) {
		try {
			lock();
			if (so.getId().equals(fRootFolder.getId())) {
				throw new CmisInvalidArgumentException(
						"Root folder cannot be renamed.");
			}
			if (so instanceof Fileable) {
				for (String folderId : ((Fileable) so).getParentIds()) {
					Folder folder = (Folder) getObjectById(folderId);
					if (hasChild(folder, newName)) {
						throw new CmisNameConstraintViolationException(
								"Cannot rename object to "
										+ newName
										+ ". This path already exists in parent "
										+ getFolderPath(folder.getId()) + ".");
					}
				}
				// remove by old path
				fStoredObjectMap.remove(((Fileable) so).getPath());
			}
			so.setName(newName);
			// add by new path
			fStoredObjectMap.put(((Fileable) so).getPath(), so);
		} finally {
			unlock();
		}
	}

	private boolean hasChild(Folder folder, String name) {
		List<Fileable> children = getChildren(folder);
		for (Fileable child : children) {
			if (child.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public List<String> getParentIds(StoredObject so, String user) {
		List<String> visibleParents = new ArrayList<String>();
		if (!(so instanceof Fileable)) {
			throw new CmisInvalidArgumentException("Object is not fileable: "
					+ so.getId());
		}
		Filing fileable = (Fileable) so;
		List<String> parents = fileable.getParentIds();
		for (String id : parents) {
			StoredObject parent = getObjectById(id);
			if (hasReadAccess(user, parent)) {
				visibleParents.add(id);
			}
		}
		return visibleParents;
	}

	public boolean hasReadAccess(String principalId, StoredObject so) {
		return hasAccess(principalId, so, Permission.READ);
	}

	public boolean hasWriteAccess(String principalId, StoredObject so) {
		return hasAccess(principalId, so, Permission.WRITE);
	}

	public boolean hasAllAccess(String principalId, StoredObject so) {
		return hasAccess(principalId, so, Permission.ALL);
	}

	public void checkReadAccess(String principalId, StoredObject so) {
		checkAccess(principalId, so, Permission.READ);
	}

	public void checkWriteAccess(String principalId, StoredObject so) {
		checkAccess(principalId, so, Permission.WRITE);
	}

	public void checkAllAccess(String principalId, StoredObject so) {
		checkAccess(principalId, so, Permission.ALL);
	}

	private void checkAccess(String principalId, StoredObject so,
			Permission permission) {
		if (!hasAccess(principalId, so, permission)) {
			throw new CmisPermissionDeniedException("Object with id "
					+ so.getId() + " and name " + so.getName()
					+ " does not grant " + permission.toString()
					+ " access to principal " + principalId);
		}
	}

	private boolean hasAccess(String principalId, StoredObject so,
			Permission permission) {
		if (null != principalId && principalId.equals(ADMIN_PRINCIPAL_ID)) {
			return true;
		}
		List<Integer> aclIds = getAllAclsForUser(principalId, permission);
		return so == null ? false : aclIds.contains(((StoredObjectImpl) so).getAclId());
	}

	private InMemoryAcl getInMemoryAcl(int aclId) {

		for (InMemoryAcl acl : fAcls) {
			if (aclId == acl.getId()) {
				return acl;
			}
		}
		return null;
	}

	private int setAcl(StoredObjectImpl so, Acl acl) {
		int aclId;
		if (null == acl || acl.getAces().isEmpty()) {
			aclId = 0;
		} else {
			aclId = getAclId(null, acl, null);
		}
		so.setAclId(aclId);
		return aclId;
	}

	/**
	 * Check if an Acl is already known.
	 * 
	 * @param acl
	 *            acl to be checked
	 * @return 0 if Acl is not known, id of Acl otherwise
	 */
	private int hasAcl(InMemoryAcl acl) {
		for (InMemoryAcl acl2 : fAcls) {
			if (acl2.equals(acl)) {
				return acl2.getId();
			}
		}
		return -1;
	}

	private int addAcl(InMemoryAcl acl) {
		int aclId = -1;

		if (null == acl) {
			return 0;
		}

		lock();
		try {
			aclId = hasAcl(acl);
			if (aclId < 0) {
				aclId = getNextAclId();
				acl.setId(aclId);
				fAcls.add(acl);
			}
		} finally {
			unlock();
		}
		return aclId;
	}

	private Acl applyAcl(StoredObject so, Acl acl) {
		int aclId = setAcl((StoredObjectImpl) so, acl);
		return getAcl(aclId);
	}

	private Acl applyAcl(StoredObject so, Acl addAces, Acl removeAces) {
		int aclId = getAclId((StoredObjectImpl) so, addAces, removeAces);
		((StoredObjectImpl) so).setAclId(aclId);
		return getAcl(aclId);
	}

	private Acl applyAclRecursive(Folder folder, Acl addAces, Acl removeAces,
			String principalId) {
		List<Fileable> children = getChildren(folder, -1, -1,
				ADMIN_PRINCIPAL_ID, false).getChildren();
		Acl result = applyAcl(folder, addAces, removeAces);

		if (null == children) {
			return result;
		}

		for (Fileable child : children) {
			if (hasAllAccess(principalId, child)) {
				if (child instanceof Folder) {
					applyAclRecursive((Folder) child, addAces, removeAces,
							principalId);
				} else {
					applyAcl(child, addAces, removeAces);
				}
			}
		}

		return result;
	}

	private Acl applyAclRecursive(Folder folder, Acl acl, String principalId) {
		List<Fileable> children = getChildren(folder, -1, -1,
				ADMIN_PRINCIPAL_ID, false).getChildren();
		Acl result = applyAcl(folder, acl);

		if (null == children) {
			return result;
		}

		for (Fileable child : children) {
			if (hasAllAccess(principalId, child)) {
				if (child instanceof Folder) {
					applyAclRecursive((Folder) child, acl, principalId);
				} else {
					applyAcl(child, acl);
				}
			}
		}

		return result;
	}

	private List<StoredObject> getAllRelationships(String objectId,
			RelationshipDirection direction) {

		List<StoredObject> res = new ArrayList<StoredObject>();

		for (StoredObject so : fStoredObjectMap.values()) {
			if (so instanceof Relationship) {
				Relationship ro = (Relationship) so;
				if (ro.getSourceObjectId().equals(objectId)
						&& (RelationshipDirection.EITHER == direction || RelationshipDirection.SOURCE == direction)) {
					res.add(so);
				} else if (ro.getTargetObjectId().equals(objectId)
						&& (RelationshipDirection.EITHER == direction || RelationshipDirection.TARGET == direction)) {
					res.add(so);
				}
			}
		}
		return res;
	}

	public boolean isTypeInUse(String typeId) {
		// iterate over all the objects and check for each if the type matches
		for (String objectId : getIds()) {
			StoredObject so = getObjectById(objectId);
			if (so.getTypeId().equals(typeId)
					|| (so.getSecondaryTypeIds() != null && so
							.getSecondaryTypeIds().contains(typeId))) {
				return true;
			}
		}
		return false;
	}

	public void addParent(StoredObject so, Folder parent) {
		try {
			lock();
			if (hasChild(parent, so.getName())) {
				throw new IllegalArgumentException(
						"Cannot assign new parent folder, this name already exists in target folder.");
			}
			MultiFiling mfi;
			if (so instanceof MultiFiling) {
				mfi = (MultiFiling) so;
			} else {
				throw new IllegalArgumentException("Object " + so.getId()
						+ "is not fileable");
			}

			addParentIntern(mfi, parent);
		} finally {
			unlock();
		}
	}

	public void removeParent(StoredObject so, Folder parent) {
		try {
			lock();
			MultiFiling mfi;
			if (so instanceof MultiFiling) {
				mfi = (MultiFiling) so;
			} else {
				throw new IllegalArgumentException("Object " + so.getId()
						+ "is not fileable");
			}

			removeParentIntern(mfi, parent);
		} finally {
			unlock();
		}
	}

	private void addParentIntern(MultiFiling so, Folder parent) {
		so.addParentId(parent.getId());
	}

	private void removeParentIntern(MultiFiling so, Folder parent) {
		so.removeParentId(parent.getId());
	}

	private static void sortFolderList(List<? extends StoredObject> list) {
		// TODO evaluate orderBy, for now sort by path segment
		class FolderComparator implements Comparator<StoredObject> {

			public int compare(StoredObject f1, StoredObject f2) {
				String segment1 = f1.getName();
				String segment2 = f2.getName();

				return segment1.compareTo(segment2);
			}
		}

		Collections.sort(list, new FolderComparator());
	}

	public ContentStream getContent(StoredObject so, long offset, long length) {
		if (so instanceof Content) {
			Content content = (Content) so;
			ContentStream contentStream = content.getContent();
			if (null == contentStream && so.getId().length() <= 3) {
				return null;
			} else if (this.persistenceManager != null) {
				return this.persistenceManager.readContent(
						this.persistenceManager.getFile(so, fStoredObjectMap),
						false);
			} else {
				return ((ContentStreamDataImpl) contentStream)
						.getCloneWithLimits(offset, length);
			}
		} else {
			throw new CmisInvalidArgumentException(
					"Cannot set content, object does not implement interface Content.");
		}
	}

	public ContentStream setContent(StoredObject so, ContentStream contentStream) {
		if (contentStream == null &&
				so instanceof Content &&
				((Content) so).getContent() != null &&
				((Content) so).getContent().getFileName() != null) {
			so.getStore().getPersistenceManager().deleteFromDisk(so);
		}
		if (contentStream == null) return null;
		String fileName = contentStream.getFileName();
		try {
			if (so instanceof Content) {
				ContentStreamDataImpl newContent;
				Content content = (Content) so;

				if (null == contentStream) {
					newContent = null;
				} else {
					boolean useFakeContentStore = so.getTypeId().equals(
							DefaultTypeSystemCreator.BIG_CONTENT_FAKE_TYPE);
					newContent = new ContentStreamDataImpl(
							MAX_CONTENT_SIZE_KB == null ? 0
									: MAX_CONTENT_SIZE_KB, useFakeContentStore);
					if (null == fileName || fileName.length() <= 0) {
						fileName = so.getName(); // use name of document as
													// fallback
					}
					String extension = FilenameUtils.getExtension(fileName);
					if (extension != null && !extension.equals(""))
						extension = "." + extension;
					if (so.getId() == null) {
						String id = so.getStore().getPersistenceManager().generateId();
						so.setId(id + extension);
						// special case of duplication createDocumentFromSource
						if (so.getName() != null) {
    						extension = FilenameUtils.getExtension(so.getName());
    						so.setId(id + "." + extension);
						}
					}
					newContent.setPersistencemanager(so.getStore()
							.getPersistenceManager());
					String mimeType = contentStream.getMimeType();
					if (null == mimeType || mimeType.length() <= 0) {
						mimeType = "application/octet-stream"; // use as
																// fallback
					}
					newContent.setMimeType(mimeType);
					newContent.setLastModified(new GregorianCalendar());
					fileName = so.getStore().getPersistenceManager()
						.getFile(so, fStoredObjectMap).getAbsolutePath();
					newContent.setFileName(fileName);
					try {
						newContent.setContent(contentStream.getStream());
					} catch (IOException e) {
						throw new CmisRuntimeException(
								"Failed to get content from InputStream", e);
					}
				}
				content.setContent(newContent);
				return newContent;

			} else {
				throw new CmisInvalidArgumentException(
						"Cannot set content, object does not implement interface Content.");
			}
		} finally {
			// new File(fileName).deleteOnExit();
		}
	}

	public void appendContent(StoredObject so, ContentStream contentStream) {
		if (so instanceof Content) {
			Content content = (Content) so;
			ContentStreamDataImpl newContent = (ContentStreamDataImpl) content
					.getContent();

			if (null == newContent) {
				content.setContent(null);
			} else {
				try {
					newContent.appendContent(contentStream.getStream());
				} catch (IOException e) {
					throw new CmisStorageException(
							"Failed to append content: IO Exception", e);
				}
			}
		} else {
			throw new CmisInvalidArgumentException(
					"Cannot set content, object does not implement interface Content.");
		}
	}

	public List<RenditionData> getRenditions(StoredObject so,
			String renditionFilter, long maxItems, long skipCount) {

		return RenditionUtil.getRenditions(so, renditionFilter, maxItems,
				skipCount);
	}

	public ContentStream getRenditionContent(StoredObject so, String streamId,
			long offset, long length) {
		return RenditionUtil.getRenditionContent(so, streamId, offset, length);
	}

	public PersistenceManager getPersistenceManager() {
		return persistenceManager;
	}

}
