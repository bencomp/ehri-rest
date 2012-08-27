/**
 * TODO Add license text
 */
package eu.ehri.data;

import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;

import com.tinkerpop.blueprints.pgm.Edge;
import com.tinkerpop.blueprints.pgm.TransactionalGraph;
import com.tinkerpop.blueprints.pgm.Vertex;
import com.tinkerpop.blueprints.pgm.impls.neo4j.Neo4jGraph;

import java.util.Iterator;
import java.util.Map;

/**
 * Main purpose is to be used by the ehri-plugin to provide a REST API to the neo4j service
 * Adds functionality that would otherwise require several neo4j calls 
 * and when possible also hides neo4j specifics and use more generic GrapgDb names.
 * neo4j Node => Vertex
 * neo4j Relationship => Edge 
 * 
 */
public class EhriNeo4j {
	/**
	 * 
	 */
	private EhriNeo4j() { }

	/**
	 * 
	 * @param graphDb	The graph database
	 * @param index
	 * @param field
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public static Iterator<Vertex> simpleQuery(
		GraphDatabaseService graphDb, String index, String field, String query) throws Exception {
		Neo4jGraph graph = new Neo4jGraph(graphDb);
		Iterator<Vertex> iter = graph.getIndex(index, Vertex.class).get(field, query).iterator();
		return iter;
	}
	
	/*** Vertices ***/
	
	/**
	 * Create a vertex (also known as Node) and have it indexed
	 * 
	 * TODO maybe have it return a Vertex instead of a Node or rename it to createNodeIndexed?
	 * Also remove the Index from the name?
	 * 
	 * @param graphDb		The graph database
	 * @param data 			The properties
	 * @param indexName		The name of the index
	 * @return 				The node created
	 * @throws Exception
	 */
	public static Node createIndexedVertex(GraphDatabaseService graphDb, Map<String, Object> data, String indexName) throws Exception 
	{
		Neo4jGraph graph = new Neo4jGraph(graphDb);
		IndexManager manager = graphDb.index();
		
		graph.setMaxBufferSize(0);
		graph.startTransaction();
		try {
			Index<Node> index = manager.forNodes(indexName);
			Node node = graphDb.createNode();
			
			addProperties(index, node, data);
			
			graph.stopTransaction(TransactionalGraph.Conclusion.SUCCESS);
			return node;
		} catch (Exception e) {
			graph.stopTransaction(TransactionalGraph.Conclusion.FAILURE);
			throw e;
		}
	}
		
	/**
	 * Delete vertex with its edges
	 * Neo4j requires you delete all adjacent edges first. 
	 * Blueprints' removeVertex() method does that; the Neo4jServer DELETE URI does not.
	 * 
	 * @param graphDb	The graph database
	 * @param id		The vertex identifier
	 */
	public static void deleteVertex(GraphDatabaseService graphDb, long id)
	{
		Neo4jGraph graph = new Neo4jGraph(graphDb);

		Vertex vertex = graph.getVertex(id);
		graph.removeVertex(vertex);
	}
		
	/**
	 * Update a vertex
	 * 
	 * @param graphDb	The graph database
	 * @param id		The vertex identifier
	 * @param data		The properties
	 * @param indexName	The name of the index
	 * @return
	 * @throws Exception
	 */
	public static Node updateIndexedVertex(GraphDatabaseService graphDb, long id, Map<String, Object> data, String indexName) throws Exception 
	{
		Neo4jGraph graph = new Neo4jGraph(graphDb);
		IndexManager manager = graphDb.index();
		
		graph.setMaxBufferSize(0);
		graph.startTransaction();
		try {
			Index<Node> index = manager.forNodes(indexName);
			Node node = graphDb.getNodeById(id);
			
			replaceProperties(index, node, data);
		
			graph.stopTransaction(TransactionalGraph.Conclusion.SUCCESS);
			return node;
		} catch (Exception e) {
			graph.stopTransaction(TransactionalGraph.Conclusion.FAILURE);
			throw e;
		}
	}
	
	/*** Edges ***/
	
	/**
	 * Create an indexed Edge
	 * 
	 * @param graphDb	The graph database
	 * @param outV		The outgoing vertex
	 * @param typeLabel	The edge type
	 * @param inV		The ingoing Vertex
	 * @param data		The properties
	 * @param indexName	The name of the index
	 * @return
	 * @throws Exception
	 */
	public static Relationship createIndexedEdge(GraphDatabaseService graphDb, long outV, String typeLabel, long inV, Map<String, Object> data, String indexName) throws Exception 
	{
		Neo4jGraph graph = new Neo4jGraph(graphDb);
		IndexManager manager = graphDb.index();
		// an enum needed here?
		DynamicRelationshipType relationshipType = DynamicRelationshipType.withName(typeLabel); 
		
		graph.setMaxBufferSize(0);
		graph.startTransaction();
		try {
			Node node = graphDb.getNodeById(outV);
			
			RelationshipIndex index = manager.forRelationships(indexName);
		
			Relationship relationship = node.createRelationshipTo(graphDb.getNodeById(inV), relationshipType);
			
			addProperties(index, relationship, data);
			
			graph.stopTransaction(TransactionalGraph.Conclusion.SUCCESS);
			return relationship;
		} catch (Exception e) {
			graph.stopTransaction(TransactionalGraph.Conclusion.FAILURE);
			throw e;
		}
	}
	
	
	/**
	 * Delete Edge
	 * 
	 * @param graphDb	The graph database
	 * @param id		The edge identifier
	 */
	public static void deleteEdge(GraphDatabaseService graphDb, long id)
	{
		Neo4jGraph graph = new Neo4jGraph(graphDb);
		
		Edge edge = graph.getEdge(id);
		graph.removeEdge(edge);
	}
	
	/**
	 * Update Edge
	 * similar to update Vertex, because the Type cannot be changed
	 * 
	 * @param graphDb	The graph database
	 * @param id		The edge identifier
	 * @param data		The properties
	 * @param indexName	The name of the index
	 * @return
	 * @throws Exception
	 */
	public static Relationship updateIndexedEdge(GraphDatabaseService graphDb, long id, Map<String, Object> data, String indexName) throws Exception 
	{
		Neo4jGraph graph = new Neo4jGraph(graphDb);
		IndexManager manager = graphDb.index();
		
		graph.setMaxBufferSize(0);
		graph.startTransaction();
		try {
			RelationshipIndex index = manager.forRelationships(indexName);
			Relationship relationship = graphDb.getRelationshipById(id);
			
			replaceProperties(index, relationship, data);
			
			graph.stopTransaction(TransactionalGraph.Conclusion.SUCCESS);
			return relationship;
		} catch (Exception e) {
			graph.stopTransaction(TransactionalGraph.Conclusion.FAILURE);
			throw e;
		}
	}	
	
	// create_indexed_vertex_with_subordinates(data, index_name, subs)
	// update_indexed_vertex_with_subordinates(_id, data, index_name, subs) 
	  
	/*** index ***/
	// get_or_create_vertex_index(index_name, index_params)
	// get_or_create_edge_index(index_name, index_params)

	
    /*** helpers ***/

	/**
	 * Replace properties to a property container like vertex and edge
	 * 
	 * @param index		The index of the container
	 * @param c			The container Edge or Vertex
	 * @param data		The properties
	 */
	private static void replaceProperties(Index index, PropertyContainer c, Map<String, Object> data)
	{
		// remove container from index
		index.remove(c);
		
		// remove 'old' properties
		for (String key : c.getPropertyKeys())
			c.removeProperty(key);
		
		// add all 'new' properties to the relationship and index
		addProperties(index, c, data);
	}
	
	/**
	 * Add properties to a property container like vertex and edge
	 * 
	 * @param index	The index of the container
	 * @param c		The container Edge or Vertex
	 * @param data	The properties
	 */
	private static void addProperties(Index index, PropertyContainer c, Map<String, Object> data)
	{
		// TODO data cannot be null
		
		for(Map.Entry<String, Object> entry : data.entrySet()) {
			if (entry.getValue() == null)
				continue;
			c.setProperty(entry.getKey(), entry.getValue());
			index.add(c, entry.getKey(), String.valueOf(entry.getValue()));
		}
	}
}