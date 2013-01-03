package eu.ehri.project.views.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jVertex;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.PipeFunction;

import eu.ehri.project.acl.AclManager;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.IndexNotFoundException;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.utils.ClassUtils;
import eu.ehri.project.views.Search;
import eu.ehri.project.views.ViewHelper;

/**
 * Handles querying Accessible Entities, with ACL semantics.
 * 
 * TODO: Possibly refactor more of the ACL logic into AclManager.
 * 
 * @author mike
 * 
 * @param <E>
 */
public final class Query<E extends AccessibleEntity> implements Search<E> {
    private final Optional<Integer> offset;
    private final Optional<Integer> limit;
    private final boolean page;

    private final FramedGraph<Neo4jGraph> graph;
    private final GraphManager manager;
    private final Class<E> cls;
    private final ViewHelper helper;
    private final PermissionScope scope;

    /**
     * Full Constructor.
     * 
     * @param graph
     * @param cls
     * @param scope
     * @param offset
     * @param limit
     * @param sort
     * @param page
     */
    public Query(FramedGraph<Neo4jGraph> graph, Class<E> cls,
            PermissionScope scope, Optional<Integer> offset,
            Optional<Integer> limit, Boolean page) {
        this.graph = graph;
        this.cls = cls;
        this.scope = scope;
        this.offset = offset;
        this.limit = limit;
        this.page = page;
        helper = new ViewHelper(graph, scope);
        manager = GraphManagerFactory.getInstance(graph);
    }

    /**
     * Simple constructor.
     * 
     * @param graph
     * @param cls
     */
    public Query(FramedGraph<Neo4jGraph> graph, Class<E> cls) {
        this(graph, cls, SystemScope.getInstance(),
                Optional.<Integer> absent(), Optional.<Integer> absent(), false);
    }

    /**
     * Scoped Constructor.
     * 
     * @param graph
     * @param cls
     * @param scope
     */
    public Query(FramedGraph<Neo4jGraph> graph, Class<E> cls,
            PermissionScope scope) {
        this(graph, cls, scope, Optional.<Integer> absent(), Optional
                .<Integer> absent(), false);
    }

    /**
     * Copy constructor.
     * 
     * @param other
     */
    public Query<E> copy(Query<E> other) {
        return new Query<E>(other.graph, other.cls, other.scope, other.offset,
                other.limit, other.page);
    }

    /**
     * Class representing a page of content.
     * 
     * @param <T>
     *            the item type
     */
    public static class Page<T> {
        private Iterable<T> iterable;
        private long count;
        private Integer offset;
        private Integer limit;

        Page(Iterable<T> iterable, long count, Integer offset, Integer limit) {
            this.iterable = iterable;
            this.count = count;
            this.offset = offset;
            this.limit = limit;
        }

        public Iterable<T> getIterable() {
            return iterable;
        }

        public long getCount() {
            return count;
        }

        public Integer getOffset() {
            return offset;
        }

        public Integer getLimit() {
            return limit;
        }
    }

    /**
     * Wrapper method for FramedVertexIterables that converts a
     * FramedVertexIterable<T> back into a plain Iterable<Vertex>.
     * 
     * @param <T>
     */
    public static class FramedVertexIterableAdaptor<T extends VertexFrame>
            implements Iterable<Vertex> {
        Iterable<T> iterable;

        public FramedVertexIterableAdaptor(final Iterable<T> iterable) {
            this.iterable = iterable;
        }

        public Iterator<Vertex> iterator() {
            return new Iterator<Vertex>() {
                private Iterator<T> iterator = iterable.iterator();

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                public boolean hasNext() {
                    return this.iterator.hasNext();
                }

                public Vertex next() {
                    return this.iterator.next().asVertex();
                }
            };
        }
    }

    /**
     * Fetch an item by property id. The first matching item will be returned.
     * 
     * @param key
     * @param value
     * @param user
     * @return The matching framed vertex.
     * @throws PermissionDenied
     * @throws ItemNotFound
     */
    public E get(String id, Accessor user) throws PermissionDenied,
            ItemNotFound {
        E item = manager.getFrame(id, cls);
        helper.checkReadAccess(item, user);
        return item;
    }

    /**
     * Fetch an item by property key/value. The first matching item will be
     * returned.
     * 
     * @param key
     * @param value
     * @param user
     * @return The matching framed vertex.
     * @throws PermissionDenied
     * @throws ItemNotFound
     */
    public E get(String key, String value, Accessor user)
            throws PermissionDenied, ItemNotFound {
        CloseableIterable<Vertex> indexQuery = manager.getVertices(key, value,
                ClassUtils.getEntityType(cls));
        try {
            E item = graph.frame(indexQuery.iterator().next(), cls);
            helper.checkReadAccess(item, user);
            return item;
        } catch (NoSuchElementException e) {
            throw new ItemNotFound(key, value);
        } finally {
            indexQuery.close();
        }
    }

    /**
     * Return a Page instance containing a count of total items, and an iterable
     * for the given offset/limit.
     * 
     * @param user
     * @return Page instance
     */
    public Page<E> page(Accessor user) {
        return page(ClassUtils.getEntityType(cls), user);
    }

    /**
     * Return a Page instance containing a count of total items, and an iterable
     * for the given offset/limit.
     * 
     * @param user
     * @return Page instance
     * @throws IndexNotFoundException
     */
    public Page<E> page(EntityClass type, Accessor user) {
        return page(manager.getFrames(type, cls), user);
    }

    /**
     * Return a Page instance containing a count of total items, and an iterable
     * for the given offset/limit.
     * 
     * @param iterable
     * @param user
     * 
     * @return Page instance
     * @throws IndexNotFoundException
     */
    public Page<E> page(Iterable<E> vertices, Accessor user) {
        return page(vertices, user, cls);
    }

    /**
     * Return a Page instance containing a count of total items, and an iterable
     * for the given offset/limit.
     * 
     * @param iterable
     * @param user
     * 
     * @return Page instance
     * @throws IndexNotFoundException
     */
    public <T extends VertexFrame> Page<T> page(Iterable<T> vertices,
            Accessor user, Class<T> cls) {
        // This function is optimised for ACL actions.
        // FIXME: Work out if there's any way of doing, in Gremlin or
        // Cypher, a count that doesn't require re-iterating the results on
        // a completely new index query. This seems stupid.

        PipeFunction<Vertex, Boolean> aclFilterFunction = new AclManager(graph)
                .getAclFilterFunction(user);

        // FIXME: We have to read the vertices into memory here since we
        // can't re-use the iterator for counting and streaming.
        ArrayList<Vertex> userVerts = Lists
                .newArrayList(new GremlinPipeline<E, Vertex>(
                        new FramedVertexIterableAdaptor<T>(vertices)).filter(
                        aclFilterFunction).iterator());

        return new Page<T>(
                graph.frameVertices(
                        setPipelineRange(new GremlinPipeline<Vertex, Vertex>(
                                userVerts)), cls), userVerts.size(),
                offset.orNull(), limit.orNull());
    }

    /**
     * Return a Page instance containing a count of total items, and an iterable
     * for the given offset/limit.
     * 
     * @param key
     * @param query
     * @param user
     * 
     * @return Page instance
     * @throws IndexNotFoundException
     */
    public Page<E> page(String key, String query, Accessor user) {
        // This function is optimised for ACL actions.
        // FIXME: Work out if there's any way of doing, in Gremlin or
        // Cypher, a count that doesn't require re-iterating the results on
        // a completely new index query. This seems stupid.
        CloseableIterable<Neo4jVertex> countQ = manager.getVertices(key, query,
                ClassUtils.getEntityType(cls));
        try {
            CloseableIterable<Neo4jVertex> indexQ = manager.getVertices(key,
                    query, ClassUtils.getEntityType(cls));
            try {
                PipeFunction<Vertex, Boolean> aclFilterFunction = new AclManager(
                        graph).getAclFilterFunction(user);
                long count = new GremlinPipeline<Vertex, Vertex>(countQ)
                        .filter(aclFilterFunction).count();
                return new Page<E>(graph.frameVertices(
                        setPipelineRange(new GremlinPipeline<Vertex, Vertex>(
                                indexQ).filter(aclFilterFunction)), cls),
                        count, offset.orNull(), limit.orNull());
            } finally {
                indexQ.close();
            }
        } finally {
            countQ.close();
        }
    }

    /**
     * Return an iterable for all items accessible to the user.
     * 
     * @param user
     * @return Iterable of framed vertices accessible to the given user
     */
    public Iterable<E> list(Accessor user) {
        return list(ClassUtils.getEntityType(cls), user);
    }

    /**
     * List items accessible to a given user.
     * 
     * @param user
     * 
     * @return Iterable of items accessible to the given accessor
     * @throws IndexNotFoundException
     */
    public Iterable<E> list(String key, String query, Accessor user) {
        // This function is optimised for ACL actions.
        CloseableIterable<Neo4jVertex> vertices = manager.getVertices(key,
                query, ClassUtils.getEntityType(cls));
        try {
            GremlinPipeline<E, Vertex> filter = new GremlinPipeline<E, Vertex>(
                    vertices).filter(new AclManager(graph)
                    .getAclFilterFunction(user));
            return graph.frameVertices(setPipelineRange(filter), cls);
        } finally {
            vertices.close();
        }
    }

    /**
     * List items accessible to a given user.
     * 
     * @param vertices
     * @param user
     * 
     * @return Iterable of items accessible to the given accessor
     * @throws IndexNotFoundException
     */
    public Iterable<E> list(Iterable<E> vertices, Accessor user) {
        return list(vertices, user, cls);
    }

    /**
     * List items accessible to a given user.
     * 
     * @param vertices
     * @param user
     * 
     * @return Iterable of items accessible to the given accessor
     * @throws IndexNotFoundException
     */
    public <T extends VertexFrame> Iterable<T> list(Iterable<T> vertices,
            Accessor user, Class<T> cls) {
        GremlinPipeline<T, Vertex> filter = new GremlinPipeline<T, Vertex>(
                new FramedVertexIterableAdaptor<T>(vertices))
                .filter(new AclManager(graph).getAclFilterFunction(user));
        return graph.frameVertices(setPipelineRange(filter), cls);
    }

    /**
     * Return an iterable for all items accessible to the user.
     * 
     * @param user
     * @return Iterable of framed vertices accessible to the given user
     * @throws IndexNotFoundException
     */
    public Iterable<E> list(EntityClass type, Accessor user) {
        return list(manager.getFrames(type, cls), user);
    }

    /**
     * Get the offset applied to this query.
     * 
     * @return
     */
    public int getOffset() {
        return offset.orNull();
    }

    /**
     * Set the offset applied to this query.
     * 
     * @param offset
     */
    public Query<E> setOffset(Integer offset) {
        return new Query<E>(this.graph, this.cls, this.scope,
                Optional.fromNullable(offset), this.limit, this.page);
    }

    /**
     * Get the limit applied to this query.
     * 
     * @return
     */
    public int getLimit() {
        return limit.orNull();
    }

    /**
     * Set the limit applied to this query.
     * 
     * @param limit
     */
    public Query<E> setLimit(Integer limit) {
        return new Query<E>(this.graph, this.cls, this.scope, this.offset,
                Optional.fromNullable(limit), this.page);
    }

    // Helpers

    private <EE> GremlinPipeline<EE, Vertex> setPipelineRange(
            GremlinPipeline<EE, Vertex> filter) {
        int low = Math.max(offset.or(0), 0);
        int high = low + Math.max(limit.or(-1), 0) - 1;
        return filter.range(low, high);
    }
}
