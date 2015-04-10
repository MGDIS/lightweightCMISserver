package org.apache.chemistry.opencmis.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStorageException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStreamNotSupportedException;
import org.apache.chemistry.opencmis.commons.impl.MimeTypes;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.chemistry.opencmis.commons.impl.json.JSONObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Document;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Fileable;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Relationship;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.DocumentImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.FilingImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.FolderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilePersistence implements IPersistenceManager {

	private static final Logger LOG = LoggerFactory
			.getLogger(FilePersistence.class.getName());

	private String rootId = "@root@";

	public FilePersistence() {

	}

	/** Root directory. */
	private File root = null;

	public void setRootPath(String rootPath) {
		if (rootPath == null)
			return;
		root = new File(rootPath);
	}

	public String getRootPath() {
		if (root == null)
			return null;
		return root.getAbsolutePath();
	}

	public void setRootId(String rootId) {
		this.rootId = rootId;
	}

	public String getRootId() {
		return rootId;
	}

	/**
	 * Returns the File object by id or throws an appropriate exception.
	 */
	public File getFile(String id) {
		try {
			return new File(id);
		} catch (Exception e) {
			throw new CmisObjectNotFoundException(e.getMessage(), e);
		}
	}

	/**
	 * Returns the File object by id or throws an appropriate exception.
	 */
	public File getFile(StoredObject so,
			Map<String, StoredObject> storedObjectMap) {
		try {
			String path = "";
			if (so instanceof Fileable) {
				List<String> parentIds = ((Fileable) so).getParentIds();
				if (parentIds != null && parentIds.size() > 0) {
					String id = parentIds.get(0);
					while (!id.equals(getRootId())) {
						String folderPath = storedObjectMap.get(id).getName();
						path = folderPath + "/" + path;
						id = ((Folder) storedObjectMap.get(id)).getParentId();
					}
					path = getRootPath() + "/" + path;
				}
			}
			return new File(path, so.getName());
		} catch (Exception e) {
			throw new CmisObjectNotFoundException(e.getMessage(), e);
		}
	}

	/**
	 * Returns the metadataFile from objectId
	 * 
	 * @param id
	 * @return
	 */
	public File getMetadataFile(String id) {
		try {
			return new File(new File(id).getAbsolutePath() + ".metadata");
		} catch (Exception e) {
			throw new CmisObjectNotFoundException(e.getMessage(), e);
		}
	}

	/**
	 * Converts an id to a File object. A simple and insecure implementation,
	 * but good enough for now.
	 */
	private File idToFile(String id) throws IOException {
		if (id == null || id.length() == 0) {
			throw new CmisInvalidArgumentException("Id is not valid!");
		}

		if (id.equals(rootId)) {
			return root;
		}

		// return new File(root, (new String(
		// Base64.decode(id.getBytes("US-ASCII")), "UTF-8")).replace('/',
		// File.separatorChar));
		return new File(id);
	}

	/**
	 * Read file content.
	 */
	public ContentStream readContent(File file, boolean closeOnEnd) {

		if (root == null)
			return null;

		if (!file.isFile()) {
			throw new CmisStreamNotSupportedException(file.getAbsolutePath()
					+ " is not a file!");
		}

		if (file.length() == 0) {
			LOG.warn("Document (" + file.getAbsolutePath()
					+ ") has no content!");
		}

		InputStream stream = null;
		try {
			// stream = new FileInputStream(file);
			stream = org.apache.commons.io.FileUtils.openInputStream(file);

			LOG.info("Read content from " + file.getAbsolutePath());
			// stream = new BufferedInputStream(new FileInputStream(file),
			// BUFFER_SIZE);

			// compile data
			ContentStreamImpl result;
			result = new ContentStreamImpl();

			result.setFileName(file.getName());
			result.setLength(BigInteger.valueOf(file.length()));
			result.setMimeType(MimeTypes.getMIMEType(file));
			result.setStream(stream);

			return result;
		} catch (IOException e) {
			throw new CmisRuntimeException(e.getMessage(), e);
		} finally {
			if (stream != null)
				try {
					if (closeOnEnd)
						stream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	/**
	 * Get stream content.
	 */
	public InputStream getStream(File file) {

		if (root == null)
			return null;

		if (!file.isFile()) {
			throw new CmisStreamNotSupportedException(file.getAbsolutePath()
					+ " is not a file!");
		}

		if (file.length() == 0) {
			LOG.warn("Document (" + file.getAbsolutePath()
					+ ") has no content!");
		}

		InputStream stream = null;
		try {
			// stream = new FileInputStream(file);
			stream = org.apache.commons.io.FileUtils.openInputStream(file);

			LOG.info("Get stream content from " + file.getAbsolutePath());
			// stream = new BufferedInputStream(new FileInputStream(file),
			// BUFFER_SIZE);

			return stream;
		} catch (IOException e) {
			throw new CmisRuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Writes the content to disc.
	 */
	@SuppressWarnings("resource")
	public int writeContent(File newFile, InputStream stream) {

		if (root == null)
			return 0;

		try {
			Files.copy(stream, Paths.get(newFile.getAbsolutePath()),
					StandardCopyOption.REPLACE_EXISTING);
			int length = (int) newFile.length();
			// Files.copy(stream, Paths.get(newFile.getAbsolutePath()),
			// StandardCopyOption.REPLACE_EXISTING);
			LOG.info("Write content in " + newFile.getAbsolutePath());
			return length;
		} catch (IOException e) {
			throw new CmisStorageException("Could not write content in "
					+ newFile + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Returns the id of stored object
	 */
	public String getId(StoredObject so) {
		return so.getId();
		/*
		 * try { if (so.getName().equals(getRootPath())) { return rootId; } if
		 * (so instanceof Fileable) { return fileToId(new File(getRootPath(),
		 * ((Fileable) so).getPathSegment())); } else { return fileToId(new
		 * File(getRootPath(), so.getName())); } } catch (Exception e) { throw
		 * new CmisRuntimeException(e.getMessage(), e); }
		 */
	}

	/**
	 * Returns the id of a File object or throws an appropriate exception.
	 */
	public String getId(File file) {
		try {
			return fileToId(file);
		} catch (Exception e) {
			throw new CmisRuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Creates a File object from an id. A simple and insecure implementation,
	 * but good enough for now.
	 */
	private String fileToId(File file) throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("File is not valid!");
		}

		if (root == null)
			return null;

		if (root.equals(file)) {
			return rootId;
		}

		// return Base64.encodeBytes(getRepositoryPath(file).getBytes("UTF-8"));
		return file.getName();
	}

	/**
	 * Writes the metadata to disc.
	 */
	public void writeCMISToDisc(File newFile, StoredObject so) {

		if (root == null)
			return;

		// PrintWriter out = null;
		String metadataFile = newFile.getAbsolutePath() + ".metadata";
		try {
			// out = new PrintWriter();
			JSONObject json = new StoredObjectJsonSerializer().serialize(so);
			org.apache.commons.io.FileUtils.writeStringToFile(new File(
					metadataFile), json.toString());
			// out.print(json);
			// out.flush();
			LOG.info("Writing metadata in " + metadataFile);
		} catch (IOException e) {
			throw new CmisStorageException("Could not write metadata: "
					+ e.getMessage(), e);
		}
	}

	/**
	 * Read the metadata from disc.
	 */
	public StoredObject readCMISFromDisk(File metadataFile) {

		if (root == null)
			return null;

		if (!metadataFile.exists()) {
			return null;
		}

		String storedObjectStr = "";
		try {
			storedObjectStr = org.apache.commons.io.FileUtils
					.readFileToString(metadataFile);

			LOG.info("Read metadata from " + metadataFile.getAbsolutePath());
			// storedObjectStr = readAllLines(new FileReader(file));
		} catch (IOException e) {
			LOG.warn("When filtering with metadata", e);
			return null;
		}
		// File content = new
		// File(metadataFile.getAbsolutePath().replace(".metadata", ""));
		StoredObject result = null;
		// if (!content.isDirectory()) {
		// result = new StoredObjectJsonSerializer()
		// .deserialize(storedObjectStr);
		// } else {
		LOG.debug(storedObjectStr);
		result = new StoredObjectJsonSerializer().deserialize(storedObjectStr);
		// }

		return result;
	}

	/**
	 * Delete file from disc
	 */
	public void deleteFromDisk(StoredObject so) {

		if (root == null)
			return;

		File contentFile = getFile(so.getId());
		File metadataFile = getMetadataFile(so.getId());

		// delete content file
		delete(contentFile);
		// delete metadata file
		delete(metadataFile);
	}

	private void delete(File file) {

		if (file.isDirectory()) {

			// directory is empty, then delete it
			if (file.list().length == 0) {

				file.delete();
				LOG.info("Directory is deleted : " + file.getAbsolutePath());

			} else {

				// list all the directory contents
				String files[] = file.list();

				for (String temp : files) {
					// construct the file structure
					File fileDelete = new File(file, temp);

					// recursive delete
					delete(fileDelete);
				}

				// check the directory again, if empty then delete it
				if (file.list().length == 0) {
					file.delete();
					LOG.info("Directory is deleted : " + file.getAbsolutePath());
				}
			}

		} else {
			// if file, then delete it
			file.delete();
			LOG.info("File is deleted : " + file.getAbsolutePath());
		}
	}

	public File calculateFile(Map<String, StoredObject> storedObjectMap,
			StoredObject so) {
		if (getRootPath() == null)
			return null;
		StringBuilder stb = new StringBuilder(so.getName());
		String parent = getParent(so);
		while (parent != null && parent.length() > 0) {
			StoredObject parentSO = storedObjectMap.get(parent);
			if (parentSO.getName() == null)
				return null;
			stb.insert(0, parentSO.getName() + "/");
			parent = getParent(parentSO);
		}
		String path = stb.toString();
		if (path.equals("")) {
			path = getRootPath();
		}

		// if (so instanceof VersionedDocument) {
		// path += "-" + ((VersionedDocument)
		// so).getLatestVersion(major)getVersionLabel();
		// }

		if (path.equals(getRootPath()))
			return new File(getRootPath());

		if (so instanceof Document) return new File(Long.toString(System.nanoTime(), 36));
		else return new File(path);
	}

	private static String getParent(StoredObject so) {
		String parent = null;
		if (so instanceof Folder) {
			parent = ((Folder) so).getParentId();
		} else if (so instanceof Fileable) {
			List<String> parents = ((Fileable) so).getParentIds();
			if (parents.size() > 0) {
				parent = parents.get(0);
			}
		}
		return parent;
	}

	private String replacePathByObjectId(String path, StoredObject so,
			Map<String, StoredObject> storedObjectMap) {
		/*
		if (so instanceof FolderImpl) {
			if (((FolderImpl) so).getParentId() == null)
				return path;
		}
		// replace folderName by its objectId in path
		for (String parentId : ((Fileable) so).getParentIds()) {
			if (parentId.equals(getRootId()))
				return path;
			// get folder
			StoredObject folder = storedObjectMap.get(parentId);
			if (folder != null && parentId != null && folder.getName() != null) {
				path = path.replace(folder.getName(), parentId);
				if (((Fileable) so).getParentIds().size() > 0)
					path = replacePathByObjectId(path, folder, storedObjectMap);
			}
		}
		*/
		return path;
	}

	public void saveObject(Map<String, StoredObject> storedObjectMap,
			StoredObject so, boolean withContent) {
		String path = "";
		if (so instanceof Fileable && null != ((Fileable) so).getParentIds()) {
			if (((Fileable) so).getParentIds().size() > 0) {
				String parent = getFullPath(storedObjectMap, ((Fileable) so)
						.getParentIds().get(0));
				path = parent;
			} else {
				path = getRootPath();
			}
		} else if (so instanceof Relationship) {
			path = getRootPath();
		}

		// replace folderName by its objectId in path
		if (so instanceof Fileable) {
			if (((Fileable) so).getParentIds().size() > 0) {
				path = replacePathByObjectId(path, so, storedObjectMap);
			}
		}

		// check the file
		if (getRootPath() != null) {
			File newFile = null;
			if (so.getName().equals(getRootPath())) {
				newFile = new File(getRootPath());
			} else {
				newFile = new File(path, so.getName());
			}
			if (!newFile.exists()) {
				// create the file
				try {
					if (so instanceof FilingImpl) {
						newFile.createNewFile();
					} else if (so instanceof FolderImpl) {
						newFile.mkdirs();
					}
				} catch (IOException e) {
					throw new CmisStorageException(
							"Could not create file or directory : "
									+ e.getMessage(), e);
				}
			}
			// write content
			if (withContent && so instanceof DocumentImpl) {
				ContentStream contentStream = ((DocumentImpl) so).getContent();

				// write content, if available
				if (contentStream != null && contentStream.getStream() != null) {
					writeContent(newFile, contentStream.getStream());
				}
			}
			// save properties
			writeCMISToDisc(newFile, so);
		}
	}

	private String getFullPath(Map<String, StoredObject> storedObjectMap,
			String folderId) {
		String parent = "";
		while (folderId != null) {
			StoredObject parentStore = storedObjectMap.get(folderId);
			parent = parentStore.getName() + File.separator + parent;
			folderId = ((FolderImpl) parentStore).getParentId();
		}
		return parent;
	}

	@Override
	public void moveObject(Map<String, StoredObject> storedObjectMap,
			StoredObject so, Folder newParent) throws IOException {
		// move data
		File file = getFile(so, storedObjectMap);
		File metadata = new File(file.getAbsolutePath() + ".metadata");
		org.apache.commons.io.FileUtils.moveToDirectory(
				file, 
				getFile(newParent, storedObjectMap), false);
		// move metadata
		org.apache.commons.io.FileUtils.moveToDirectory(
				metadata, 
				getFile(newParent, storedObjectMap), false);
	}

}