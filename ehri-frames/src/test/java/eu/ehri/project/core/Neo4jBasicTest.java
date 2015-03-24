/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * Uses the embedded neo4j database initial code from the neo4j tutorials
 * 
 */
public class Neo4jBasicTest {
    protected GraphDatabaseService graphDb;

    @Before
    public void prepareTestDatabase() {
        graphDb = new TestGraphDatabaseFactory()
                .newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    @After
    public void destroyTestDatabase() {
        graphDb.shutdown();
    }

    @Test
    public void shouldCreateNode() {
        Transaction tx = graphDb.beginTx();

        Node n = null;
        try {
            n = graphDb.createNode();
            n.setProperty("name", "Nancy");
            tx.success();
        } catch (Exception e) {
            tx.failure();
        } finally {
            tx.finish();
        }

        // The node should have an id greater than 0, which is the id of the
        // reference node.
        // assertThat( n.getId(), is( greaterThan( 0l ) ) );
        assertNotNull(n.getId());
        assertTrue(n.getId() > 0);

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        Node foundNode = graphDb.getNodeById(n.getId());
        // assertThat( foundNode.getId(), is( n.getId() ) );
        // assertThat( (String) foundNode.getProperty( "name" ), is( "Nancy" )
        // );
        assertEquals(foundNode.getId(), n.getId());
        assertEquals(foundNode.getProperty("name"), "Nancy");
    }
}
