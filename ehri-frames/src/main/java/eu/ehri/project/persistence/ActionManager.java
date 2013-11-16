package eu.ehri.project.persistence;

import com.google.common.base.Optional;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.base.ItemHolder;
import eu.ehri.project.models.events.SystemEvent;
import eu.ehri.project.models.events.SystemEventQueue;
import eu.ehri.project.models.events.Version;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Iterator;

/**
 * Class for dealing with actions.
 *
 * Events are captured as a linked list with new events placed
 * at the head. The head event is connected to a node called the
 * {@link eu.ehri.project.models.events.SystemEventQueue}. Events
 * can have subjects (the thing the event is happening to) and
 * actioners (the person initiating the event.) A subject's events
 * and an actioner's actions likewise for a linked list so it is
 * possible to fetch new events easily and prevent having to sort
 * by timestamp, etc. Schematically, the graph thus formed looks
 * something like:
 *
 *      Actioner            SystemEventQueue             Subject
 *          \/                      \/                      \/
 *  [lifecycleAction]   [lifecycleActionStream]     [lifecycleEvent]
 *          |                       |                       |
 *          e3--[hasActioner]-<-- Event 3 ---[hasEvent]--<--e3
 *          \/                      \/                      \/
 *  [lifecycleAction]       [lifecycleAction]       [lifecycleEvent]
 *          |                       |                       |
 *          e2--[hasActioner]-<-- Event 2 ---[hasEvent]--<--e2
 *          \/                      \/                      \/
 *  [lifecycleAction]       [lifecycleAction]       [lifecycleEvent]
 *          |                       |                       |
 *          e1--[hasActioner]-<-- Event 1 ---[hasEvent]--<--e1
 *
 *
 * @author michaelb
 */
public final class ActionManager {

    // Name of the global event root node, from whence event
    // streams propagate.
    public static final String GLOBAL_EVENT_ROOT = "globalEventRoot";
    public static final String DEBUG_TYPE = "_debugType";
    public static final String EVENT_LINK = "eventLink";
    public static final String LINK_TYPE = "_linkType";

    private final FramedGraph<?> graph;
    private final GraphManager manager;
    private final Frame scope;
    private final Serializer versionSerializer;

    /**
     * Constructor with scope.
     *
     * @param graph
     */
    public ActionManager(final FramedGraph<?> graph, final Frame scope) {
        this.graph = graph;
        this.manager = GraphManagerFactory.getInstance(graph);
        this.scope = Optional.fromNullable(scope).or(SystemScope.getInstance());
        this.versionSerializer = new Serializer.Builder(graph).dependentOnly().build();
    }

    /**
     * Constructor.
     *
     * @param graph
     */
    public ActionManager(FramedGraph<?> graph) {
        this(graph, SystemScope.getInstance());
    }

    /**
     * EventContext is a handle to a particular action to which additional
     * subjects can be added.
     *
     * @author mike
     */
    public static class EventContext {
        private final ActionManager actionManager;
        private final SystemEvent systemEvent;
        private final Actioner actioner;
        private final EventTypes actionType;
        private final Optional<String> logMessage;
        private final Optional<Object> payload;

        public EventContext(ActionManager actionManager, SystemEvent systemEvent,
                Actioner actioner, EventTypes type, Optional<String> logMessage,
                Optional<Object> payload) {
            this.actionManager = actionManager;
            this.actionType = type;
            this.systemEvent = systemEvent;
            this.actioner = actioner;
            this.logMessage = logMessage;
            this.payload = payload;
        }

        public EventContext(ActionManager actionManager, SystemEvent systemEvent,
                Actioner actioner, EventTypes type, Optional<String> logMessage) {
            this(actionManager, systemEvent, actioner, type, logMessage, Optional.absent());
        }

        public EventContext(ActionManager actionManager, SystemEvent systemEvent,
                Actioner actioner, EventTypes type, Optional<String> logMessage, Object payload) {
            this(actionManager, systemEvent, actioner, type, logMessage,
                    Optional.fromNullable(payload));
        }

        public SystemEvent getSystemEvent() {
            return this.systemEvent;
        }

        /**
         * Get the event actioner.
         * @return
         */
        public Actioner getActioner() {
            return this.actioner;
        }
        
        /**
         * Get the event context log message.
         * @return
         */
        public Optional<String> getLogMessage() {
            return this.logMessage;
        }

        public EventContext createVersion(Frame frame) {
            try {
                Bundle bundle = actionManager.versionSerializer.vertexFrameToBundle(frame);
                return createVersion(frame, bundle);
            } catch (SerializationError serializationError) {
                throw new RuntimeException(serializationError);
            }
        }

        public EventContext createVersion(Frame frame, Bundle bundle) {
            try {
                Bundle version = new Bundle(EntityClass.VERSION)
                        .withDataValue(Ontology.VERSION_ENTITY_ID, frame.getId())
                        .withDataValue(Ontology.VERSION_ENTITY_CLASS, frame.getType())
                        .withDataValue(Ontology.VERSION_ENTITY_DATA, bundle.toJson());
                Version ev = new BundleDAO(actionManager.graph)
                        .create(version, Version.class);
                actionManager.replaceAtHead(frame.asVertex(), ev.asVertex(),
                        Ontology.ENTITY_HAS_PRIOR_VERSION,
                        Ontology.ENTITY_HAS_PRIOR_VERSION, Direction.OUT);
                actionManager.graph.addEdge(null, ev.asVertex(),
                        systemEvent.asVertex(), Ontology.VERSION_HAS_EVENT);

                return this;
            } catch (ValidationError validationError) {
                throw new RuntimeException(validationError);
            }
        }

        /**
         * Add subjects to an event.
         * @param entities
         * @return
         */
        public EventContext addSubjects(AccessibleEntity... entities) {
            for (AccessibleEntity entity : entities) {
                Vertex vertex = actionManager.getLinkNode(
                        Ontology.ENTITY_HAS_LIFECYCLE_EVENT);
                actionManager.replaceAtHead(entity.asVertex(), vertex,
                        Ontology.ENTITY_HAS_LIFECYCLE_EVENT,
                        Ontology.ENTITY_HAS_LIFECYCLE_EVENT, Direction.OUT);
                actionManager.addSubject(systemEvent.asVertex(), vertex);
            }
            return this;
        }
    }

    /**
     * Get the latest global event.
     * @return
     */
    public SystemEvent getLatestGlobalEvent() {
        try {
            SystemEventQueue sys = manager.getFrame(GLOBAL_EVENT_ROOT, EntityClass.SYSTEM, SystemEventQueue.class);
            Iterable<SystemEvent> latest = sys.getSystemEvents();
            return latest.iterator().hasNext() ? latest.iterator().next() : null;
        } catch (ItemNotFound itemNotFound) {
            throw new RuntimeException("Fatal error: system node (id: 'system') was not found. " +
                    "Perhaps the graph was incorrectly initialised?");
        }
    }

    /**
     * Get an iterable of global events in most-recent-first order.
     * @return
     */
    public Iterable<SystemEvent> getLatestGlobalEvents() {
        try {
            SystemEventQueue queue = manager.getFrame(
                    GLOBAL_EVENT_ROOT, EntityClass.SYSTEM, SystemEventQueue.class);
            return queue.getSystemEvents();
        } catch (ItemNotFound itemNotFound) {
            throw new RuntimeException("Couldn't find system event queue!");
        }
    }

    /**
     * Create a global event and insert it at the head of the system queue. The
     * relationship from the <em>system</em> node to the new latest action is
     * <em>actionType</em><strong>Stream</strong>.
     *
     * @param actionType
     * @param logMessage
     * @return
     */
    private SystemEvent createGlobalEvent(EventTypes actionType, Optional<String> logMessage) {
        try {
            Vertex system = manager.getVertex(GLOBAL_EVENT_ROOT, EntityClass.SYSTEM);
            Bundle ge = new Bundle.Builder(EntityClass.SYSTEM_EVENT)
                    .addDataValue(Ontology.EVENT_TYPE, actionType.toString())
                    .addDataValue(Ontology.EVENT_TIMESTAMP, getTimestamp())
                    .addDataValue(Ontology.EVENT_LOG_MESSAGE, logMessage.or(""))
                    .build();
            SystemEvent ev = new BundleDAO(graph).create(ge, SystemEvent.class);
            if (!scope.equals(SystemScope.getInstance())) {
                ev.setEventScope(scope);
            }
            replaceAtHead(system, ev.asVertex(), Ontology.ACTIONER_HAS_LIFECYCLE_ACTION + "Stream", Ontology.ACTIONER_HAS_LIFECYCLE_ACTION, Direction.OUT);
            return ev;
        } catch (ItemNotFound e) {
            e.printStackTrace();
            throw new RuntimeException("Fatal error: system node (id: 'system') was not found. " +
                    "Perhaps the graph was incorrectly initialised?");
        } catch (ValidationError e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unexpected validation error creating action", e);
        }
    }

    /**
     * Create an action with the given type.
     * @param user
     * @param type
     * @return
     */
    public EventContext logEvent(Actioner user, EventTypes type) {
        return logEvent(user, type, Optional.<String>absent());
    }

    /**
     * Create an action with the given type and a log message.
     * @param user
     * @param type
     * @param logMessage
     * @return
     */
    public EventContext logEvent(Actioner user, EventTypes type, String logMessage) {
        return logEvent(user, type, Optional.of(logMessage));
    }

    /**
     * Create an action with the given type and a log message.
     * @param user
     * @param type
     * @param logMessage
     * @return
     */
    public EventContext logEvent(Actioner user, EventTypes type, String logMessage, Object payload) {
        return logEvent(user, type, Optional.of(logMessage));
    }

    /**
     * Create an action node describing something that user U has done.
     *
     * @param user
     * @param type
     * @param logMessage
     * @return
     */
    public EventContext logEvent(Actioner user, EventTypes type, Optional<String> logMessage) {
        Vertex vertex = getLinkNode(Ontology.ACTIONER_HAS_LIFECYCLE_ACTION);
        replaceAtHead(user.asVertex(), vertex,
                Ontology.ACTIONER_HAS_LIFECYCLE_ACTION,
                Ontology.ACTIONER_HAS_LIFECYCLE_ACTION, Direction.OUT);
        SystemEvent globalEvent = createGlobalEvent(type, logMessage);
        addSubject(globalEvent.asVertex(), vertex);
        return new EventContext(this, globalEvent, user, type, logMessage);
    }

    /**
     * Create an action for the given subject, user, and type.
     * @param subject
     * @param user
     * @param type
     * @return
     */
    public EventContext logEvent(AccessibleEntity subject, Actioner user,
            EventTypes type) {
        return logEvent(subject, user, type, Optional.<String>absent());
    }

    /**
     * Create an action for the given subject, user, and type and a log message.
     * @param subject
     * @param user
     * @param type
     * @param logMessage
     * @return
     */
    public EventContext logEvent(AccessibleEntity subject, Actioner user,
            EventTypes type, String logMessage) {
        return logEvent(subject, user, type, Optional.of(logMessage));
    }

    /**
     * Create an action node that describes what user U has done with subject S
     * via logMessage log.
     *
     * @param subject
     * @param user
     * @param logMessage
     * @return
     */
    public EventContext logEvent(AccessibleEntity subject, Actioner user,
            EventTypes type, Optional<String> logMessage) {
        EventContext context = logEvent(user, type, logMessage);
        context.addSubjects(subject);
        return context;
    }

    public ActionManager setScope(Frame frame) {
        return new ActionManager(graph,
                Optional.fromNullable(frame).or(SystemScope.getInstance()));
    }


    // Helpers.

    /**
     * Create a link vertex. This we stamp with a descriptive
     * type purely for debugging purposes.
     */
    private Vertex getLinkNode(String linkType) {
        Vertex vertex = graph.addVertex(null);
        vertex.setProperty(DEBUG_TYPE, EVENT_LINK);
        vertex.setProperty(LINK_TYPE, linkType);
        return vertex;
    }

    private void addSubject(Vertex event, Vertex subject) {
        Long count = event.getProperty(ItemHolder.CHILD_COUNT);
        graph.addEdge(null, subject, event, Ontology.ENTITY_HAS_EVENT);
        if (count == null) {
            event.setProperty(ItemHolder.CHILD_COUNT, 1L);
        } else {
            event.setProperty(ItemHolder.CHILD_COUNT, count + 1L);
        }
    }

    /**
     * Given a vertex <em>head</em> that forms that start of a chain <em>relation</em> with
     * direction <em>direction</em>, insert vertex <em>insert</em> <strong>after</strong>
     * the head of the chain.
     *
     * @param head
     * @param newHead
     * @param relation
     * @param direction
     */
    private void replaceAtHead(Vertex head, Vertex newHead, String headRelation,
            String relation, Direction direction) {
        Iterator<Vertex> iter = head.getVertices(direction, headRelation).iterator();
        if (iter.hasNext()) {
            Vertex current = iter.next();
            for (Edge e : head.getEdges(direction, headRelation)) {
                graph.removeEdge(e);
            }
            graph.addEdge(null, newHead, current, relation);

        }
        graph.addEdge(null, head, newHead, headRelation);
    }

    /**
     * Get the current time as a timestamp.
     * @return
     */
    public static String getTimestamp() {
        DateTime dt = DateTime.now();
        return ISODateTimeFormat.dateTime().print(dt);
    }
}