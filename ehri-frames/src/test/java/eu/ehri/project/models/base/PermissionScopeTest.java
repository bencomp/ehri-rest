package eu.ehri.project.models.base;

import com.google.common.collect.Lists;
import eu.ehri.project.models.Country;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.Repository;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.helpers.collection.Iterables;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class PermissionScopeTest extends AbstractFixtureTest {
    public DocumentaryUnit doc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doc = manager.getFrame("c2", DocumentaryUnit.class);
    }

    @Test
    public void testGetPermissionScopes() throws Exception {
        List<PermissionScope> scopes = Lists.newArrayList(
                manager.getFrame("c1", PermissionScope.class),
                manager.getFrame("r1", PermissionScope.class),
                manager.getFrame("nl", PermissionScope.class));
        assertEquals(scopes, Iterables.toList(doc.getPermissionScopes()));
    }

    @Test
    public void testIdChain() throws Exception {
        assertEquals(Lists.newArrayList("nl", "r1", "c1", "c2"), doc.idChain());
    }
}
