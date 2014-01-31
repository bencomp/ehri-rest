package eu.ehri.project.models.base;

import com.google.common.collect.Lists;
import eu.ehri.project.models.Country;
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
    public Repository repository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        repository = manager.getFrame("r1", Repository.class);
    }

    @Test
    public void testGetPermissionScopes() throws Exception {
        List<PermissionScope> scopes = Lists.newArrayList(
                (PermissionScope)manager.getFrame("nl", Country.class));
        assertEquals(scopes, Iterables.toList(repository.getPermissionScopes()));
    }

    @Test
    public void testIdChain() throws Exception {
        assertEquals(Lists.newArrayList("nl", "r1"), repository.idChain());
    }
}
