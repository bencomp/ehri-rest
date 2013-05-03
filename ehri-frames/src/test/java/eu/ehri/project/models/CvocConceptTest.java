package eu.ehri.project.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import eu.ehri.project.models.base.IdentifiableEntity;
import eu.ehri.project.test.ModelTestBase;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import com.tinkerpop.blueprints.TransactionalGraph.Conclusion;
import com.tinkerpop.blueprints.Vertex;

import eu.ehri.project.definitions.Entities;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.ConceptDescription;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistance.Bundle;
import eu.ehri.project.persistance.BundleDAO;
import eu.ehri.project.views.Crud;
import eu.ehri.project.views.impl.CrudViews;

public class CvocConceptTest extends ModelTestBase {

    /**
     * Just play a bit with a small 'graph' of concepts.
     * 
     * NOTE: Better wait for the improved testing 'fixture' before doing
     * extensive testing on Concepts
     * 
     * @throws Exception
     */
    @SuppressWarnings("serial")
    @Test
    public void testConceptHierarchy() throws Exception {
        // Fruit, Apples and Bananas etc.

        Map<String, Object> data = new HashMap<String, Object>() {
            {
                put(IdentifiableEntity.IDENTIFIER_KEY, "fruit");
            }
        };
        Vertex v_fruit = manager.createVertex("fruit_id",
                EntityClass.CVOC_CONCEPT, data);

        data = new HashMap<String, Object>() {
            {
                put(IdentifiableEntity.IDENTIFIER_KEY, "apples");
            }
        };
        Vertex v_apples = manager.createVertex("applies_id",
                EntityClass.CVOC_CONCEPT, data);

        data = new HashMap<String, Object>() {
            {
                put(IdentifiableEntity.IDENTIFIER_KEY, "bananas");
            }
        };
        Vertex v_bananas = manager.createVertex("bananas_id",
                EntityClass.CVOC_CONCEPT, data);

        data = new HashMap<String, Object>() {
            {
                put(IdentifiableEntity.IDENTIFIER_KEY, "trees");
            }
        };
        Vertex v_trees = manager.createVertex("trees_id",
                EntityClass.CVOC_CONCEPT, data);

        // OK, so now we have fruit and more....
        // See if we can frame them
        Concept fruit = graph.frame(v_fruit, Concept.class);
        Concept apples = graph.frame(v_apples, Concept.class);
        Concept bananas = graph.frame(v_bananas, Concept.class);
        Concept trees = graph.frame(v_trees, Concept.class);

        // OK, framed, now construct relations etc.
        Transaction tx = graph.getBaseGraph().getRawGraph().beginTx();
        try {
            fruit.addNarrowerConcept(apples);
            fruit.addNarrowerConcept(bananas);
            tx.success();
        } catch (Exception e) {
            tx.failure();
        } finally {
            tx.finish();
        }

        // fruit should now be the broader concept
        assertEquals(fruit.getId(), apples.getBroaderConcepts()
                .iterator().next().getId());
        assertEquals(fruit.getId(), bananas.getBroaderConcepts()
                .iterator().next().getId());

        // make a relation to Trees concept
        tx = graph.getBaseGraph().getRawGraph().beginTx();
        try {
            apples.addRelatedConcept(trees);
            tx.success();
        } catch (Exception e) {
            tx.failure();
        } finally {
            tx.finish();
        }

        // is it symmetric?
        assertEquals(apples.getId(), trees.getRelatedByConcepts()
                .iterator().next().getId());

        // TODO test removal of a relation
    }

    private String TEST_LABEL_LANG = "en-US";

    // @formatter:off
    @SuppressWarnings("serial")
    protected Map<String, Object> getAppleTestBundle() {
        // Data structure representing a not-yet-created collection.
        // Using double-brace initialization to ease the pain.
        return new HashMap<String, Object>() {{
            put(Bundle.ID_KEY, null);
            put(Bundle.TYPE_KEY, Entities.CVOC_CONCEPT);
            put(Bundle.DATA_KEY, new HashMap<String, Object>() {{
                put(IdentifiableEntity.IDENTIFIER_KEY, "apple");
            }});
            put(Bundle.REL_KEY, new HashMap<String, Object>() {{
                put("describes", new LinkedList<HashMap<String, Object>>() {{
                    add(new HashMap<String, Object>() {{
                        //put(Bundle.ID_KEY, "cvd1");
                        put(Bundle.TYPE_KEY, Entities.CVOC_CONCEPT_DESCRIPTION);
                        put(Bundle.DATA_KEY, new HashMap<String, Object>() {{
                            put(Description.LANGUAGE_CODE, TEST_LABEL_LANG);
                            put(ConceptDescription.PREFLABEL, "pref1");
                            // other properties are optional, but we put them in
                            put("altLabel", new String[]{"alt1","alt2"});
                            put(ConceptDescription.DEFINITION, new String[]{"def1"}); // allow multiple
                            put(ConceptDescription.SCOPENOTE, new String[]{"sn1"}); // allow multiple
                        }});
                    }});
                }});
            }});
        }};
    }
    // @formatter:on

    @Test
    public void testCreateConceptWithDescription() throws Exception {
        UserProfile validUser = manager.getFrame("mike", UserProfile.class);
        Crud<Concept> conceptViews = new CrudViews<Concept>(graph,
                Concept.class);
        Bundle bundle = Bundle.fromData(getAppleTestBundle());

        Concept concept = null;
        concept = conceptViews.create(bundle, validUser);
        graph.getBaseGraph().stopTransaction(Conclusion.SUCCESS);

		// Does the label have the correct properties
		assertNotNull(concept);
		
		// test for description
		Description description = concept.getDescriptions().iterator().next();
		assertEquals(TEST_LABEL_LANG, description.getLanguageOfDescription());
		
		//String[] altLabels = ((ConceptDescription)description).getAltLabels();		
		// NOTE: use framing on the vertex to get the Model class
		// that is the frames way of doning things
		
		ConceptDescription descr = graph.frame(description.asVertex(), ConceptDescription.class);		
		assertEquals("pref1", descr.getPrefLabel());
		// etc. etc.
		
		//String[] altLabels = descr.getAltLabels();		
		//assertEquals("alt2", altLabels[1]);
		// NOTE we can't call getAltLabels() on the interface, because it is optional
		String[] altLabels = (String[])descr.asVertex().getProperty(ConceptDescription.ALTLABEL);
		assertFalse(altLabels == null);
		assertEquals(2, altLabels.length);
		assertEquals("alt2", altLabels[1]);
	}
    
	@SuppressWarnings("serial")
    @Test
	public void testAddConceptToVocabulary() throws Exception {
		UserProfile validUser = manager.getFrame("mike", UserProfile.class);

		Map<String, Object> data = new HashMap<String, Object>() {{put(IdentifiableEntity.IDENTIFIER_KEY, "testVocabulary");}};
		Vertex v_voc = manager.createVertex("voc_id", EntityClass.CVOC_VOCABULARY, data);
		data = new HashMap<String, Object>() {{put(IdentifiableEntity.IDENTIFIER_KEY, "apples");}};
		Vertex v_apples = manager.createVertex("applies_id", EntityClass.CVOC_CONCEPT, data);

		// frame it
		Vocabulary vocabulary = graph.frame(v_voc, Vocabulary.class);
		Concept apples = graph.frame(v_apples, Concept.class);

		// now add the apples to the vocabulary
		Transaction tx = graph.getBaseGraph().getRawGraph().beginTx();
		try {
			vocabulary.addConcept(apples);
			tx.success();
		} catch (Exception e) {
			tx.failure();
		} finally {
			tx.finish();
		}
        assertEquals(vocabulary.getIdentifier(), apples.getVocabulary().getIdentifier());

	}
	
	// test creation of a vocabulary using the BundleDAO
	@Test
	public void testCreateVocabulary() throws Exception {
		String name = "voc-test-id";
		
	  	System.out.println("Creating vocabulary: \"" + name + "\"");
        Map<String, Object> vocabularyData = new HashMap<String, Object>();
        vocabularyData.put(IdentifiableEntity.IDENTIFIER_KEY, name);
        //vocabularyData.put(EntityType.ID_KEY, name);
        //vocabularyData.put("name", name);
        Bundle bundle = new Bundle(EntityClass.CVOC_VOCABULARY, vocabularyData);
        bundle = bundle.withId(name);
        
         Vocabulary vocabulary = new BundleDAO(graph).create(bundle, Vocabulary.class);
         assertEquals(name, vocabulary.getIdentifier());
	}
}