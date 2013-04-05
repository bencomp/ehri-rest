package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;

import com.tinkerpop.frames.annotations.gremlin.GremlinGroovy;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.base.*;

@EntityType(EntityClass.REPOSITORY)
public interface Repository extends AccessibleEntity, DescribedEntity,
        AnnotatableEntity, PermissionScope {

    public static final String HELDBY = "heldBy";

    @Adjacency(label = HELDBY, direction = Direction.IN)
    public Iterable<DocumentaryUnit> getCollections();

    @GremlinGroovy("_().in('" + HELDBY + "')"
        + ".copySplit(_(), _().as('n').in('" + DocumentaryUnit.CHILD_OF + "')"
                + ".loop('n'){true}{true}).fairMerge()")
    public Iterable<DocumentaryUnit> getAllCollections();

    @Adjacency(label = HELDBY, direction = Direction.IN)
    public void addCollection(final TemporalEntity collection);
}