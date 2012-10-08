package eu.ehri.extension;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.neo4j.graphdb.GraphDatabaseService;

import eu.ehri.project.models.EntityTypes;
import eu.ehri.project.models.Group;

/**
 * Provides a RESTfull interface for the Group class. 
 */
@Path(EntityTypes.GROUP)
public class GroupResource extends EhriNeo4jFramedResource<Group> {

    public GroupResource(@Context GraphDatabaseService database) {
        super(database, Group.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:\\d+}")
    public Response getGroup(@PathParam("id") long id) {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:[\\w-]+}")
    public Response getGroup(@PathParam("id") String id) {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    public StreamingOutput listGroups() {
        return list();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("")
    public Response createGroup(String json) {
        return create(json);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("")
    public Response updateGroup(String json) {
        return update(json);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteGroup(@PathParam("id") long id) {
        return delete(id);
    }
}