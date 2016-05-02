package org.apache.chemistry.opencmis.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.MutablePropertyData;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionContainer;
import org.apache.chemistry.opencmis.commons.enums.Cardinality;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.chemistry.opencmis.commons.impl.JSONConstants;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.BindingsObjectFactoryImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyBooleanImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyDateTimeImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyDecimalImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyHtmlImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyIdImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyIntegerImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyStringDefinitionImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyStringImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyUriImpl;
import org.apache.chemistry.opencmis.commons.impl.jaxb.CmisObjectType;
import org.apache.chemistry.opencmis.commons.impl.json.JSONObject;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParseException;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParser;
import org.apache.chemistry.opencmis.commons.spi.BindingsObjectFactory;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Document;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Fileable;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.MultiFiling;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoreManager;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoredObject;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.DocumentImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.FolderImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.impl.StoredObjectImpl;
import org.apache.chemistry.opencmis.server.support.TypeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoredObjectJsonSerializer {
    private static final Logger LOG = LoggerFactory
            .getLogger(StoredObjectJsonSerializer.class.getName());

    public StoredObjectJsonSerializer() {
    }

    public JSONObject serialize(final StoredObject so, TypeManager typeManager) {
        if (so == null) {
            return null;
        }

        JSONObject result = new JSONObject();

        result.put(PropertyIds.OBJECT_TYPE_ID, so.getTypeId());
        result.put(PropertyIds.OBJECT_ID, so.getId());
        result.put(PropertyIds.NAME, so.getName());

        if (so instanceof Fileable) {
            result.put(PropertyIds.PARENT_ID, ((Fileable) so).getParentIds());
        }

        if (so instanceof Folder) {
            result.put(PropertyIds.PATH, ((Folder) so).getPathSegment());
        }
        
        result.put(PropertyIds.CREATION_DATE, toString(so.getCreatedAt()));
        result.put(PropertyIds.CREATED_BY, so.getCreatedBy());
        result.put(PropertyIds.LAST_MODIFICATION_DATE,
                toString(so.getModifiedAt()));
        result.put(PropertyIds.LAST_MODIFIED_BY, so.getModifiedBy());
        result.put(PropertyIds.CHANGE_TOKEN, so.getChangeToken());

        result.put(PropertyIds.DESCRIPTION, so.getDescription());
        result.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS,
                so.getSecondaryTypeIds());

        result.put(JSONConstants.JSON_REPINFO_ID, so.getRepositoryId());

        result.put("aclId", so.getAclId());
        result.put("appliedPolicies", so.getAppliedPolicies());
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        if (so.getProperties() != null) {
            for (PropertyData<?> item : so.getProperties().values()) {
                JSONObject property = new JSONObject();
                boolean manageValues = findProperty(item.getId(), typeManager).getCardinality().equals(Cardinality.MULTI);
                String type = getType(item);
                property.put("id", item.getId());
                property.put("displayName", item.getDisplayName());
                property.put("localName", item.getLocalName());
                property.put("queryName", item.getQueryName());
                property.put("type", type);
                if (type.equals("dateTime")) {
                    property.put("firstValue",
                            toString((GregorianCalendar) item.getFirstValue()));
                    if (manageValues) {
                    	List<String> values = new ArrayList<String>();
	                    for (Object value : item.getValues()) {
	                        values.add(toString((GregorianCalendar) value));
	                    }
                    }
                } else {
                    property.put("firstValue", item.getFirstValue());
                    if (manageValues) property.put("values", item.getValues());
                }
                properties.put(item.getId(), property);
            }
        }
        result.put("properties", properties);

        return result;
    }

    private PropertyDefinition<?> findProperty(String propertyId, TypeManager typeManager){
    	return findProperty(propertyId, typeManager.getTypeDefinitionList());
    }
    
    private PropertyDefinition<?> findProperty(String propertyId, Collection<TypeDefinitionContainer> containers){
        for (TypeDefinitionContainer typeDefinitionContainer : containers) {
        	TypeDefinition typeDefinition = typeDefinitionContainer.getTypeDefinition();
        	if (typeDefinition.getPropertyDefinitions().containsKey(propertyId)) {
        		return typeDefinition.getPropertyDefinitions().get(propertyId);
        	}
        	return findProperty(propertyId, typeDefinitionContainer.getChildren());
		}
        // by default return a simple string property
        PropertyStringDefinitionImpl defaultProperty = new PropertyStringDefinitionImpl();
        defaultProperty.setCardinality(Cardinality.SINGLE);
        defaultProperty.setId(propertyId);
        defaultProperty.setPropertyType(PropertyType.STRING);
        return defaultProperty;
    }
    
    private String getType(PropertyData<?> item) {
        if (item instanceof PropertyStringImpl) {
            return "string";
        } else if (item instanceof PropertyBooleanImpl) {
            return "boolean";
        } else if (item instanceof PropertyUriImpl) {
            return "uri";
        } else if (item instanceof PropertyHtmlImpl) {
            return "html";
        } else if (item instanceof PropertyIdImpl) {
            return "id";
        } else if (item instanceof PropertyDateTimeImpl) {
            return "dateTime";
        } else if (item instanceof PropertyDecimalImpl) {
            return "decimal";
        } else if (item instanceof PropertyIntegerImpl) {
            return "integer";
        } else {
            // by default string
            return "string";
        }
    }

    private PropertyData<?> getPropertyDataFromType(String type) {
        if (type.equals("string")) {
            return new PropertyStringImpl();
        } else if (type.equals("boolean")) {
            return new PropertyBooleanImpl();
        } else if (type.equals("dateTime")) {
            return new PropertyDateTimeImpl();
        } else if (type.equals("decimal")) {
            return new PropertyDecimalImpl();
        } else if (type.equals("integer")) {
            return new PropertyIntegerImpl();
        } else if (type.equals("uri")) {
            return new PropertyUriImpl();
        } else if (type.equals("html")) {
            return new PropertyHtmlImpl();
        } else if (type.equals("id")) {
            return new PropertyIdImpl();
        } else {
            // by default string
            return new PropertyStringImpl();
        }
    }

    @SuppressWarnings("unchecked")
    public StoredObject deserialize(final String jsonString, TypeManager typeManager) {
        if (jsonString == null) {
            return null;
        }

        JSONObject result = new JSONObject();
        try {
            result = (JSONObject) new JSONParser().parse(jsonString);
        } catch (JSONParseException e) {
        	result = new JSONObject();
            e.printStackTrace();
        }
        StoredObject so = null;
        if (result.get(PropertyIds.OBJECT_TYPE_ID).equals("cmis:folder")) {
            so = new FolderImpl();
            if (result.containsKey(PropertyIds.PARENT_ID)
                    && result.get(PropertyIds.PARENT_ID) != null) {
                List<String> parentIds = (List<String>) result
                        .get(PropertyIds.PARENT_ID);
                if (parentIds != null && parentIds.size() > 0) {
                    ((Folder) so).setParentId(parentIds.get(0));
                }
            }
        } else {
            so = new DocumentImpl();
            if (result.containsKey(PropertyIds.PARENT_ID)
                    && result.get(PropertyIds.PARENT_ID) != null) {
                List<String> parentIds = (List<String>) result
                        .get(PropertyIds.PARENT_ID);
                for (String parent : parentIds) {
                    ((MultiFiling) so).addParentId(parent);
                }
            }
        }
        if (result.containsKey(PropertyIds.OBJECT_ID)
                && result.get(PropertyIds.OBJECT_ID) != null) {
            so.setId(result.get(PropertyIds.OBJECT_ID).toString());
        }
        if (result.containsKey(PropertyIds.CREATION_DATE)
                && result.get(PropertyIds.CREATION_DATE) != null) {
            so.setCreatedAt(toGregorianCalendar(result.get(
                    PropertyIds.CREATION_DATE).toString()));
        }
        if (result.containsKey(PropertyIds.CREATED_BY)
                && result.get(PropertyIds.CREATED_BY) != null) {
            so.setCreatedBy(result.get(PropertyIds.CREATED_BY).toString());
        }
        if (result.containsKey(PropertyIds.DESCRIPTION)
                && result.get(PropertyIds.DESCRIPTION) != null) {
            so.setDescription(result.get(PropertyIds.DESCRIPTION).toString());
        }
        if (result.containsKey(PropertyIds.LAST_MODIFICATION_DATE)
                && result.get(PropertyIds.LAST_MODIFICATION_DATE) != null) {
            so.setModifiedAt(toGregorianCalendar(result.get(
                    PropertyIds.LAST_MODIFICATION_DATE).toString()));
        }
        if (result.containsKey(PropertyIds.LAST_MODIFIED_BY)
                && result.get(PropertyIds.LAST_MODIFIED_BY) != null) {
            so.setModifiedBy(result.get(PropertyIds.LAST_MODIFIED_BY)
                    .toString());
        }
        if (result.containsKey(PropertyIds.NAME)
                && result.get(PropertyIds.NAME) != null) {
            so.setName(result.get(PropertyIds.NAME).toString());
        }
        if (result.containsKey(JSONConstants.JSON_REPINFO_ID)
                && result.get(JSONConstants.JSON_REPINFO_ID) != null) {
            so.setRepositoryId(result.get(JSONConstants.JSON_REPINFO_ID)
                    .toString());
        }
        if (result.containsKey(PropertyIds.OBJECT_TYPE_ID)
                && result.get(PropertyIds.OBJECT_TYPE_ID) != null) {
            so.setTypeId(result.get(PropertyIds.OBJECT_TYPE_ID).toString());
        }

        Map<String, PropertyData<?>> properties = new LinkedHashMap<String, PropertyData<?>>();
        if (result.containsKey(PropertyIds.SECONDARY_OBJECT_TYPE_IDS)
                && result.get(PropertyIds.SECONDARY_OBJECT_TYPE_IDS) != null) {
            List<String> jsonSecondaryTypes = (List<String>) result
                    .get(PropertyIds.SECONDARY_OBJECT_TYPE_IDS);
            PropertyIdImpl secondaryTypes = new PropertyIdImpl(
                    PropertyIds.SECONDARY_OBJECT_TYPE_IDS, jsonSecondaryTypes);
            properties.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS,
                    secondaryTypes);
            so.getSecondaryTypeIds().addAll(secondaryTypes.getValues());
        }
        Map<String, Object> jsonProperties = (Map<String, Object>) result
                .get("properties");
        for (Map.Entry<String, Object> property : jsonProperties.entrySet()) {
        	boolean manageValues = findProperty(property.getKey(), typeManager).getCardinality().equals(Cardinality.MULTI);
            JSONObject jsonPropertyData = (JSONObject) property.getValue();
            PropertyData<?> propertyData = getPropertyDataFromType((String) jsonPropertyData
                    .get("type"));
            ((MutablePropertyData<?>) propertyData)
                    .setId((String) jsonPropertyData.get("id"));
            ((MutablePropertyData<?>) propertyData)
                    .setDisplayName((String) jsonPropertyData
                            .get("displayName"));
            ((MutablePropertyData<?>) propertyData)
                    .setLocalName((String) jsonPropertyData.get("localName"));
            ((MutablePropertyData<?>) propertyData)
                    .setQueryName((String) jsonPropertyData.get("queryName"));
            setValues(propertyData, manageValues ? (List<?>) jsonPropertyData.get("values") : null,
                    jsonPropertyData.get("firstValue") != null ? jsonPropertyData.get("firstValue").toString() : null);
            properties.put(property.getKey(), propertyData);
        }
        so.setProperties(properties);

        return so;
    }

    @SuppressWarnings("unchecked")
    private void setValues(PropertyData<?> item, List<?> values, Object value) {
        if (item instanceof PropertyStringImpl) {
            ((PropertyStringImpl) item).setValue((String) value);
            if(values != null) ((PropertyStringImpl) item).setValues((List<String>) values);
        } else if (item instanceof PropertyBooleanImpl) {
            ((PropertyBooleanImpl) item).setValue(Boolean
                    .valueOf((String) value));
            if(values != null) ((PropertyBooleanImpl) item).setValues((List<Boolean>) values);
        } else if (item instanceof PropertyUriImpl) {
            ((PropertyUriImpl) item).setValue((String) value);
            if(values != null) ((PropertyUriImpl) item).setValues((List<String>) values);
        } else if (item instanceof PropertyHtmlImpl) {
            ((PropertyStringImpl) item).setValue((String) value);
            if(values != null) ((PropertyStringImpl) item).setValues((List<String>) values);
        } else if (item instanceof PropertyIdImpl) {
            ((PropertyIdImpl) item).setValue((String) value);
            if(values != null) ((PropertyIdImpl) item).setValues((List<String>) values);
        } else if (item instanceof PropertyDateTimeImpl) {
            ((PropertyDateTimeImpl) item)
                    .setValue(toGregorianCalendar((String) value));
            if(values != null) {
	            List<String> dates = (List<String>) values;
	            List<GregorianCalendar> datetimes = new ArrayList<GregorianCalendar>();
	            if(dates != null) {
		            for (String date : dates) {
		                datetimes.add(toGregorianCalendar(date));
		            }
		            ((PropertyDateTimeImpl) item).setValues(datetimes);
	            }
            }
        } else if (item instanceof PropertyDecimalImpl) {
            ((PropertyDecimalImpl) item).setValue((BigDecimal) value);
            if(values != null) ((PropertyDecimalImpl) item).setValues((List<BigDecimal>) values);
        } else if (item instanceof PropertyIntegerImpl) {
            ((PropertyIntegerImpl) item).setValue((BigInteger) value);
            if(values != null) ((PropertyIntegerImpl) item).setValues((List<BigInteger>) values);
        } else {
            ((PropertyIdImpl) item).setValue((String) value);
            if(values != null) ((PropertyIdImpl) item).setValues((List<String>) values);
        }
    }

    private String toString(GregorianCalendar gc) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(((GregorianCalendar) gc).getTime());
    }

    private GregorianCalendar toGregorianCalendar(String value) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date();
        try {
            date = formatter.parse(value);
        } catch (ParseException e) {
            LOG.warn("When converting UTCString to GregorianCalendar", e);
        }
        Calendar calendar = formatter.getCalendar();

        calendar.setTime(date);
        return (GregorianCalendar) calendar;
    }
}
