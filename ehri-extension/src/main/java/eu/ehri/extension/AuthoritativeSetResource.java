package eu.ehri.extension;

import eu.ehri.extension.errors.BadRequester;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.models.HistoricalAgent;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.cvoc.AuthoritativeItem;
import eu.ehri.project.models.cvoc.AuthoritativeSet;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.impl.LoggingCrudViews;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

/**
 * Provides a RESTful interface for the AuthoritativeSet Also for managing the
 * HistoricalAgents that are in the AuthoritativeSet
 * 
 * @author Mike Bryant (http://github.com/mikesname)
 * 
 */
@Path(Entities.AUTHORITATIVE_SET)
public class AuthoritativeSetResource extends
        AbstractAccessibleEntityResource<AuthoritativeSet> {

    public AuthoritativeSetResource(@Context GraphDatabaseService database) {
        super(database, AuthoritativeSet.class);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    public Response getAuthoritativeSet(@PathParam("id") String id)
            throws ItemNotFound, AccessDenied, BadRequester {
        return retrieve(id);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/list")
    public Response listAuthoritativeSets() throws ItemNotFound, BadRequester {
        return page();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/count")
    public long countAuthoritativeSets() throws ItemNotFound, BadRequester {
        return count();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/list")
    public Response listAuthoritativeSetHistoricalAgents(
            @PathParam("id") String id)
            throws ItemNotFound, BadRequester, AccessDenied {
        Accessor user = getRequesterUserProfile();
        AuthoritativeSet set = views.detail(id, user);
        return streamingPage(getQuery(AuthoritativeItem.class)
                .page(set.getAuthoritativeItems(), user));
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/count")
    public long countAuthoritativeSetHistoricalAgents(
            @PathParam("id") String id)
            throws ItemNotFound, BadRequester, AccessDenied {
        Accessor user = getRequesterUserProfile();
        AuthoritativeSet set = views.detail(id, user);
        return getQuery(AuthoritativeItem.class).count(set.getAuthoritativeItems());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response createAuthoritativeSet(Bundle bundle,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        return create(bundle, accessors);
    }

    // Note: json contains id
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response updateAuthoritativeSet(Bundle bundle) throws PermissionDenied,
            IntegrityError, ValidationError, DeserializationError,
            ItemNotFound, BadRequester {
        return update(bundle);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    public Response updateAuthoritativeSet(@PathParam("id") String id, Bundle bundle)
            throws AccessDenied, PermissionDenied, IntegrityError, ValidationError,
            DeserializationError, ItemNotFound, BadRequester {
        return update(id, bundle);
    }

    @DELETE
    @Path("/{id:.+}")
    public Response deleteAuthoritativeSet(@PathParam("id") String id)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester {
        return delete(id);
    }

    /*** HistoricalAgent manipulation ***/

    @DELETE
    @Path("/{id:.+}/all")
    public Response deleteAllAuthoritativeSetHistoricalAgents(
            @PathParam("id") String id)
            throws ItemNotFound, BadRequester, AccessDenied, PermissionDenied {
        Accessor user = getRequesterUserProfile();
        AuthoritativeSet set = views.detail(id, user);
        try {
        	LoggingCrudViews<AuthoritativeItem> agentViews = new LoggingCrudViews<AuthoritativeItem>(graph,
                    AuthoritativeItem.class, set);
        	Iterable<AuthoritativeItem> agents = set.getAuthoritativeItems();
        	for (AuthoritativeItem agent : agents) {
        		agentViews.delete(agent.getId(), user);
        	}
            graph.getBaseGraph().commit();
            return Response.status(Status.OK).build();
        } catch (ValidationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } catch (SerializationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } finally {
            cleanupTransaction();
        }
    }

    /**
     * Create a top-level agent unit for this set.
     *
     * @param id The set ID
     * @param bundle The item data
     * @return A new item
     * @throws eu.ehri.project.exceptions.PermissionDenied
     * @throws eu.ehri.project.exceptions.ValidationError
     * @throws eu.ehri.project.exceptions.IntegrityError
     * @throws eu.ehri.project.exceptions.DeserializationError
     * @throws eu.ehri.project.exceptions.ItemNotFound
     * @throws eu.ehri.extension.errors.BadRequester
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/" + Entities.HISTORICAL_AGENT)
    public Response createAuthoritativeSetHistoricalAgent(@PathParam("id") String id,
            Bundle bundle, @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws AccessDenied, PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        try {
            Accessor user = getRequesterUserProfile();
            final AuthoritativeSet set = views.detail(id, user);
            return create(bundle, accessors, new Handler<HistoricalAgent>() {
                @Override
                public void process(HistoricalAgent agent) throws PermissionDenied {
                    set.addItem(agent);
                    agent.setPermissionScope(set);
                }
            }, views.setScope(set).setClass(HistoricalAgent.class));
        } finally {
            cleanupTransaction();
        }
    }
}
