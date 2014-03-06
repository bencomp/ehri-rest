package eu.ehri.project.test;

import eu.ehri.project.acl.*;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.BundleDAO;
import eu.ehri.project.views.ViewHelper;
import eu.ehri.project.views.impl.CrudViews;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Exercise various aspects of the permission system.
 * TODO: Streamline this stuff and make it more comprehensible.
 */
public class PermissionsTest extends AbstractFixtureTest {

    private UserProfile user;
    private AclManager acl;
    private ViewHelper viewHelper;

    @Before
    public void createTestUser() throws ValidationError, IntegrityError,
            ItemNotFound {
        // Add a new, fresh user with no perms to test with...
        user = new BundleDAO(graph).create(new Bundle(EntityClass.USER_PROFILE,
                (Map<String, Object>) TestData.getTestUserBundle().get("data")),
                UserProfile.class);
        views = new CrudViews<DocumentaryUnit>(graph, DocumentaryUnit.class);
        viewHelper = new ViewHelper(graph);
        acl = new AclManager(graph);
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateAsUserWithBadPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError {
        assertNotNull(views.create(Bundle.fromData(TestData.getTestDocBundle()), user));
    }

    @Test
    public void testCreateAsUserWithNewPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        assertNotNull(views.create(Bundle.fromData(TestData.getTestDocBundle()), user));
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateAsUserWithBadScopedPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError, ItemNotFound {
        Repository scope = manager.getFrame("r1", Repository.class);
        new AclManager(graph, scope).grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        assertNotNull(views.setScope(SystemScope.getInstance()).create(
                Bundle.fromData(TestData.getTestDocBundle()), user));
    }

    @Test
    public void testCreateAsUserWithGoodScopedPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError, ItemNotFound {
        Repository scope = manager.getFrame("r1", Repository.class);
        new AclManager(graph, scope).grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        assertNotNull(views.setScope(scope).create(
                Bundle.fromData(TestData.getTestDocBundle()), user));
    }

    @Test
    public void testCreateAsUserWithGoodNestedScopedPerms()
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError, ItemNotFound {
        Repository scope = manager.getFrame("r1", Repository.class);
        new AclManager(graph, scope).grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        DocumentaryUnit c1 = views.setScope(scope).create(
                Bundle.fromData(TestData.getTestDocBundle()), user);
        // We should be able to create another item with c1 as the scope,
        // and inherit the perms from r1
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle());
        DocumentaryUnit c2 = views.setScope(c1).create(bundle, user);
        assertNotNull(c2);
    }

    @Test
    public void testCreateAsUserWithGoodDoubleNestedScopedPerms()
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError, ItemNotFound {
        // Same as above, but with the repository as the scope instead of the item.
        Repository r1 = manager.getFrame("r1", Repository.class);
        new AclManager(graph, r1).grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        views.setScope(r1).create(
                Bundle.fromData(TestData.getTestDocBundle()), user);
        // We should be able to create another item with c1 as the r1,
        // and inherit the perms from r1
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle())
                .withDataValue(Ontology.IDENTIFIER_KEY, "some-id");
        DocumentaryUnit c2 = views.setScope(r1).create(bundle, user);
        assertNotNull(c2);
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateAsUserWithDifferentScopedPerms()
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError, ItemNotFound {
        Repository scope = manager.getFrame("r1", Repository.class);
        Repository badScope = manager.getFrame("r2", Repository.class);
        new AclManager(graph, scope).grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        assertNotNull(views.setScope(badScope).create(
                Bundle.fromData(TestData.getTestDocBundle()), user));
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateAsUserWithDifferentPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.DELETE);
        assertNotNull(views.create(Bundle.fromData(TestData.getTestDocBundle()), user));
    }

    @Test
    public void testDeleteAsUserWithGoodPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError, ItemNotFound {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.DELETE);
        DocumentaryUnit unit = views.create(Bundle.fromData(TestData.getTestDocBundle()),
                user);
        assertNotNull(unit);
        views.delete(unit.getId(), user);
    }

    @Test
    public void testCreateDeleteAsUserWithOwnerPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError, ItemNotFound,
            SerializationError {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.OWNER);
        DocumentaryUnit unit = views.create(Bundle.fromData(TestData.getTestDocBundle()),
                user);
        assertNotNull(unit);
        views.delete(unit.getId(), user);
    }

    @Test
    public void testCreateDeleteAsCreator() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError, ItemNotFound {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        DocumentaryUnit unit = views.create(Bundle.fromData(TestData.getTestDocBundle()),
                user);
        assertNotNull(unit);
        // Since we created with item, we should have OWNER perms and
        // be able to delete it.
        views.delete(unit.getId(), user);
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateDeleteAsUserWithWrongPerms() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError, ItemNotFound {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.ANNOTATE);
        DocumentaryUnit unit = views.create(Bundle.fromData(TestData.getTestDocBundle()),
                user);
        assertNotNull(unit);
        // Revoke my owner perms...
        acl.revokePermission(user, unit, PermissionType.OWNER);
        // This should now throw an error.
        views.delete(unit.getId(), user);
    }

    @Test(expected = ValidationError.class)
    public void testCreateWithoutRevoke() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        DocumentaryUnit unit = views.create(Bundle.fromData(TestData.getTestDocBundle()),
                user);
        assertNotNull(unit);
        // Should throw an integrity error
        views.create(Bundle.fromData(TestData.getTestDocBundle()), user);
        fail();
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateAsUserThenRevoke() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError {
        acl.grantPermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        DocumentaryUnit unit = views.create(Bundle.fromData(TestData.getTestDocBundle()),
                user);
        assertNotNull(unit);
        acl.revokePermission(user,
                viewHelper.getContentType(EntityClass.DOCUMENTARY_UNIT),
                PermissionType.CREATE);
        // Should throw an error
        views.create(Bundle.fromData(TestData.getTestDocBundle()), user);
        fail();
    }

    @Test
    public void testSetPermissionMatrix() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError, ItemNotFound {

        GlobalPermissionSet matrix = GlobalPermissionSet.newBuilder()
                .set(ContentTypes.DOCUMENTARY_UNIT, PermissionType.CREATE, PermissionType.DELETE)
                .build();

        try {
            views.create(Bundle.fromData(TestData.getTestDocBundle()), user);
            fail();
        } catch (PermissionDenied e) {
            acl.setPermissionMatrix(user, matrix);
            DocumentaryUnit unit = views.create(
                    Bundle.fromData(TestData.getTestDocBundle()), user);
            assertNotNull(unit);
            views.delete(unit.getId(), user);
        }
    }

    @Test
    public void testSetScopedPermissionMatrix() throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError,
            SerializationError, ItemNotFound {
        Repository scope = manager.getFrame("r1", Repository.class);

        GlobalPermissionSet matrix = GlobalPermissionSet.newBuilder()
                .set(ContentTypes.DOCUMENTARY_UNIT, PermissionType.CREATE, PermissionType.DELETE)
                .build();

        try {
            views.setScope(scope)
                    .create(Bundle.fromData(TestData.getTestDocBundle()), user);
            fail("Should be unable to create an item with scope: " + scope);
        } catch (PermissionDenied e) {
            acl.withScope(scope).setPermissionMatrix(user, matrix);

            try {
                views.create(Bundle.fromData(TestData.getTestDocBundle()), user);
                fail("Should be unable to create an item with no scope after setting scoped perms.");
            } catch (PermissionDenied e1) {
                DocumentaryUnit unit = views.setScope(scope).create(
                        Bundle.fromData(TestData.getTestDocBundle()), user);
                    assertNotNull(unit);
                views.setScope(scope).delete(unit.getId(), user);
            }
        }
    }
}
