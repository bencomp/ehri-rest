package eu.ehri.project.persistence;

import com.google.common.collect.*;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.persistence.utils.DataUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class containing various static conversion methods.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
final class DataConverter {

    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert an error set to a generic data structure.
     *
     * @param errorSet An error set
     * @return A generic map
     */
    public static Map<String, Object> errorSetToData(ErrorSet errorSet) {
        Map<String, Object> data = Maps.newHashMap();
        data.put(ErrorSet.ERROR_KEY, errorSet.getErrors().asMap());
        Map<String, List<Map<String, Object>>> relations = Maps.newHashMap();
        Multimap<String, ErrorSet> crelations = errorSet.getRelations();
        for (String key : crelations.keySet()) {
            List<Map<String, Object>> rels = Lists.newArrayList();
            for (ErrorSet subbundle : crelations.get(key)) {
                rels.add(errorSetToData(subbundle));
            }
            relations.put(key, rels);
        }
        data.put(ErrorSet.REL_KEY, relations);
        return data;
    }

    /**
     * Convert an error set to JSON.
     *
     * @param errorSet A set of validation errors
     * @return A JSON string
     * @throws SerializationError
     */
    public static String errorSetToJson(ErrorSet errorSet) throws SerializationError {
        Map<String, Object> data = errorSetToData(errorSet);
        try {
            // Note: defaultPrettyPrintWriter has been replaced by
            // writerWithDefaultPrettyPrinter in newer versions of
            // Jackson, though not the one available in Neo4j.
            @SuppressWarnings("deprecation")
            ObjectWriter writer = mapper.defaultPrettyPrintingWriter();
            return writer.writeValueAsString(data);
        } catch (Exception e) {
            throw new SerializationError("Error writing errorSet to JSON", e);
        }
    }

    /**
     * Convert a bundle to a generic data structure.
     *
     * @param bundle A bundle object
     * @return A generic map
     */
    public static Map<String, Object> bundleToData(Bundle bundle) {
        Map<String, Object> data = Maps.newHashMap();
        data.put(Bundle.ID_KEY, bundle.getId());
        data.put(Bundle.TYPE_KEY, bundle.getType().getName());
        data.put(Bundle.DATA_KEY, bundle.getData());
        if (bundle.hasMetaData()) {
            data.put(Bundle.META_KEY, bundle.getMetaData());
        }
        Map<String, List<Map<String, Object>>> relations = Maps.newHashMap();
        Multimap<String, Bundle> crelations = bundle.getRelations();
        for (String key : crelations.keySet()) {
            List<Map<String, Object>> rels = Lists.newArrayList();
            for (Bundle subbundle : crelations.get(key)) {
                rels.add(bundleToData(subbundle));
            }
            relations.put(key, rels);
        }
        data.put(Bundle.REL_KEY, relations);
        return data;
    }

    /**
     * Convert a bundle to JSON.
     *
     * @param bundle A bundle object
     * @return A JSON string
     * @throws SerializationError
     */
    public static String bundleToJson(Bundle bundle) throws SerializationError {
        Map<String, Object> data = bundleToData(bundle);
        try {
            // Note: defaultPrettyPrintWriter has been replaced by
            // writerWithDefaultPrettyPrinter in newer versions of
            // Jackson, though not the one available in Neo4j.
            @SuppressWarnings("deprecation")
            ObjectWriter writer = mapper.defaultPrettyPrintingWriter();
            return writer.writeValueAsString(data);
        } catch (Exception e) {
            throw new SerializationError("Error writing bundle to JSON", e);
        }
    }

    /**
     * Convert some JSON into an EntityBundle.
     *
     * @param json A JSON string
     * @return A bundle object
     * @throws DeserializationError
     */
    public static Bundle jsonToBundle(String json) throws DeserializationError {
        try {
            // FIXME: For some reason I can't fathom, a type reference is not
            // working here.
            // When I add one in for HashMap<String,Object>, the return value of
            // readValue
            // just seems to be Object ???
            return dataToBundle(mapper.readValue(json, Map.class));
        } catch (DeserializationError e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new DeserializationError("Error decoding JSON", e);
        }
    }

    /**
     * Convert generic data into a bundle.
     * <p/>
     * Prize to whomever can remove all the unchecked warnings. I don't really
     * know how else to do this otherwise.
     * <p/>
     * NB: We also strip out all NULL property values at this stage.
     *
     * @throws DeserializationError
     */
    public static Bundle dataToBundle(Object rawData)
            throws DeserializationError {

        // Check what we've been given is actually a Map...
        if (!(rawData instanceof Map<?, ?>))
            throw new DeserializationError("Bundle data must be a map value.");

        Map<?, ?> data = (Map<?, ?>) rawData;
        String id = (String) data.get(Bundle.ID_KEY);
        EntityClass type = getType(data);

        // Guava's immutable collections don't allow null values.
        // Since Neo4j doesn't either it's safest to trip these out
        // at the deserialization stage. I can't think of a use-case
        // where we'd need them.
        Map<String, Object> properties = getSanitisedProperties(data);
        return new Bundle(id, type, properties, getRelationships(data));
    }

    /**
     * Extract relationships from the bundle data.
     *
     * @param data A plain map
     * @return A
     * @throws DeserializationError
     */
    private static Multimap<String, Bundle> getRelationships(Map<?, ?> data)
            throws DeserializationError {
        Multimap<String, Bundle> relationBundles = LinkedListMultimap
                .create();

        // It's okay to pass in a null value for relationships.
        Object relations = data.get(Bundle.REL_KEY);
        if (relations == null)
            return relationBundles;

        if (relations instanceof Map) {
            for (Entry<?, ?> entry : ((Map<?, ?>) relations).entrySet()) {
                if (entry.getValue() instanceof List<?>) {
                    for (Object item : (List<?>) entry.getValue()) {
                        relationBundles.put((String) entry.getKey(),
                                dataToBundle(item));
                    }
                }
            }
        } else {
            throw new DeserializationError(
                    "Relationships value should be a map type");
        }
        return relationBundles;
    }

    private static Map<String, Object> getSanitisedProperties(Map<?, ?> data)
            throws DeserializationError {
        Object props = data.get(Bundle.DATA_KEY);
        if (props != null && props instanceof Map) {
            return sanitiseProperties((Map<?, ?>) props);
        }
        throw new DeserializationError(
                "No item data map found or data value not a map type.");
    }

    /**
     * Get the type key, which should correspond the one of the EntityTypes enum
     * values.
     *
     * @param data A data map
     * @return An EntityClass
     * @throws DeserializationError
     */
    private static EntityClass getType(Map<?, ?> data)
            throws DeserializationError {
        try {
            return EntityClass.withName((String) data.get(Bundle.TYPE_KEY));
        } catch (IllegalArgumentException e) {
            throw new DeserializationError("Bad or unknown type key: "
                    + data.get(Bundle.TYPE_KEY));
        }
    }

    /**
     * Filter null values out of a map.
     *
     * @param data A data map
     * @return A map with null values removed
     */
    private static Map<String, Object> sanitiseProperties(Map<?, ?> data) {
        Map<String, Object> cleaned = Maps.newHashMap();
        for (Entry<?, ?> entry : data.entrySet()) {
            Object value = entry.getValue();
            // Allow any null value, as long as it's not an empty array
            if (value != null && !DataUtils.isEmptySequence(value)) {
                cleaned.put((String) entry.getKey(), entry.getValue());
            }
        }
        return cleaned;
    }
}
