package eu.ehri.project.persistance;

import java.lang.reflect.Method;
import java.util.Map;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.VertexFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.utils.ClassUtils;

/**
 * Class containing static methods to convert between FramedVertex instances,
 * EntityBundles, and raw data.
 * 
 * @author michaelb
 * 
 */
public final class Serializer {

    private static final Logger logger = LoggerFactory.getLogger(Serializer.class);

    private final FramedGraph<Neo4jGraph> graph;

    /**
     * Lookup of entityType keys against their annotated class.
     */
    private final int maxTraversals;

    /**
     * Constructor.
     */
    public Serializer(FramedGraph<Neo4jGraph> graph) {
        this(graph, Fetch.DEFAULT_TRAVERSALS);
    }

    /**
     * Constructor which allows specifying depth of @Fetched traversals.
     * 
     * @param depth
     */
    public Serializer(FramedGraph<Neo4jGraph> graph, int depth) {
        this.graph = graph;
        this.maxTraversals = depth;
    }

    /**
     * Convert a vertex frame to a raw bundle of data.
     * 
     * @param item
     * @return
     * @throws SerializationError
     */
    public <T extends VertexFrame> Map<String, Object> vertexFrameToData(T item)
            throws SerializationError {
        return vertexFrameToBundle(item).toData();
    }

    /**
     * Convert a VertexFrame into an EntityBundle that includes its @Fetch'd
     * relations.
     * 
     * @param item
     * @return
     * @throws SerializationError
     */
    public <T extends VertexFrame> Bundle vertexFrameToBundle(T item)
            throws SerializationError {
        return vertexFrameToBundle(item, 0);
    }

    /**
     * Serialise a vertex frame to JSON.
     * 
     * @param item
     * @return
     * @throws SerializationError
     */
    public <T extends VertexFrame> String vertexFrameToJson(T item)
            throws SerializationError {
        return DataConverter.bundleToJson(vertexFrameToBundle(item));
    }

    /**
     * Run a callback every time a node in a subtree is encountered, starting
     * with the top-level node.
     * 
     * @param item
     * @param cb
     */
    public <T extends VertexFrame> void traverseSubtree(T item,
            final TraversalCallback cb) {
        traverseSubtree(item, 0, cb);
    }

    /**
     * Convert a VertexFrame into an EntityBundle that includes its @Fetch'd
     * relations.
     * 
     * @param item
     * @param depth
     * @return
     * @throws SerializationError
     */
    private <T extends VertexFrame> Bundle vertexFrameToBundle(T item, int depth)
            throws SerializationError {
        // FIXME: Try and move the logic for accessing id and type elsewhere.
        try {
            String id = (String) item.asVertex().getProperty(EntityType.ID_KEY);
            EntityClass type = EntityClass.withName((String) item.asVertex()
                    .getProperty(EntityType.TYPE_KEY));
            logger.trace("Serializing {} ({}) at depth {}", id, type, depth);
            ListMultimap<String, Bundle> relations = getRelationData(item,
                    depth, type.getEntityClass());
            return new Bundle(id, type, getVertexData(item.asVertex()),
                    relations);
        } catch (IllegalArgumentException e) {
            throw new SerializationError("Unable to serialize vertex: " + item,
                    e);
        }
    }

    private <T extends VertexFrame> ListMultimap<String, Bundle> getRelationData(
            T item, int depth, Class<?> cls) {
        ListMultimap<String, Bundle> relations = LinkedListMultimap.create();
        if (depth < maxTraversals) {
            Map<String, Method> fetchMethods = ClassUtils.getFetchMethods(cls);
            logger.trace(" - Fetch methods: {}", fetchMethods);
            for (Map.Entry<String, Method> entry : fetchMethods.entrySet()) {
                String relationName = entry.getKey();
                Method method = entry.getValue();

                if (shouldTraverse(relationName, method, depth)) {
                    logger.trace("Fetching relation: {}, depth {}, {}",
                            relationName, depth, method.getName());
                    try {
                        Object result = method.invoke(graph.frame(
                                item.asVertex(), cls));
                        // The result of one of these fetchMethods should either
                        // be a single VertexFrame, or a Iterable<VertexFrame>.
                        if (result instanceof Iterable<?>) {
                            for (Object d : (Iterable<?>) result) {
                                relations.put(
                                        relationName,
                                        vertexFrameToBundle((VertexFrame) d,
                                                depth + 1));
                            }
                        } else {
                            // This relationship could be NULL if, e.g. a
                            // collection has no holder.
                            if (result != null)
                                relations
                                        .put(relationName,
                                                vertexFrameToBundle(
                                                        (VertexFrame) result,
                                                        depth + 1));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(
                                "Unexpected error serializing VertexFrame", e);
                    }
                }
            }
        }
        return relations;
    }

    /**
     * Determine if traversal should proceed on a Frames relation.
     * 
     * @param relationName
     * @param method
     * @param depth
     * @return
     */
    private boolean shouldTraverse(String relationName, Method method, int depth) {
        // In order to avoid @Fetching the whole graph we track the
        // depth parameter and increase it for every traversal.
        // However the @Fetch annotation can also specify a maximum
        // depth of traversal beyong which we don't serialize.
        Fetch fetchProps = method.getAnnotation(Fetch.class);
        if (fetchProps == null)
            return false;

        if (depth >= fetchProps.depth()) {
            logger.trace(
                    "Terminating fetch because depth exceeded depth on fetch clause: {}, depth {}, limit {}, {}",
                    relationName, depth, fetchProps.depth());
            return false;
        }

        // If the fetch should only be serialized at a certain depth and
        // we've exceeded that, don't serialize.
        if (fetchProps.ifDepth() != -1 && depth > fetchProps.ifDepth()) {
            logger.trace(
                    "Terminating fetch because ifDepth clause found on {}, depth {}, {}",
                    relationName, depth);
            return false;
        }
        return true;
    }

    /**
     * Fetch a map of data from a vertex.
     */
    private Map<String, Object> getVertexData(Vertex item) {
        Map<String, Object> data = Maps.newHashMap();
        for (String key : item.getPropertyKeys()) {
            if (!(key.equals(EntityType.ID_KEY) || key
                    .equals(EntityType.TYPE_KEY)))
                data.put(key, item.getProperty(key));
        }
        return data;
    }

    /**
     * Run a callback every time a node in a subtree is encountered, starting
     * with the top-level node.
     * 
     * @param item
     * @param depth
     * @param cb
     */
    private <T extends VertexFrame> void traverseSubtree(T item, int depth,
            final TraversalCallback cb) {

        if (depth < maxTraversals) {
            Class<?> cls = EntityClass.withName(
                    (String) item.asVertex().getProperty(EntityType.TYPE_KEY))
                    .getEntityClass();
            Map<String, Method> fetchMethods = ClassUtils.getFetchMethods(cls);
            for (Map.Entry<String, Method> entry : fetchMethods.entrySet()) {

                String relationName = entry.getKey();
                Method method = entry.getValue();
                if (shouldTraverse(relationName, method, depth)) {
                    try {
                        Object result = method.invoke(graph.frame(
                                item.asVertex(), cls));
                        if (result instanceof Iterable<?>) {
                            int rnum = 0;
                            for (Object d : (Iterable<?>) result) {
                                cb.process((VertexFrame) d, depth,
                                        entry.getKey(), rnum);
                                traverseSubtree((VertexFrame) d, depth + 1, cb);
                                rnum++;
                            }
                        } else {
                            if (result != null) {
                                cb.process((VertexFrame) result, depth,
                                        entry.getKey(), 0);
                                traverseSubtree((VertexFrame) result,
                                        depth + 1, cb);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(
                                "Unexpected error serializing VertexFrame", e);
                    }
                }
            }
        }
    }
}
