package eu.ehri.project.persistence;

import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.test.AbstractFixtureTest;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * Test for Bundle data conversion functions.
 */
public class DataConverterTest extends AbstractFixtureTest {

    @Test
    public void testBundleToJson() throws Exception {
        DocumentaryUnit c1 = manager.getFrame("c1", DocumentaryUnit.class);
        Bundle bundle = new Serializer(graph).vertexFrameToBundle(c1)
                .withDataValue("testarray", new String[] { "one", "two", "three" })
                .withDataValue("itemWithLt", "I should be escape because of: <>");

        String json = bundle.toJson();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readValue(json, JsonNode.class);
        assertFalse(rootNode.path(Bundle.ID_KEY).isMissingNode());
        assertFalse(rootNode.path(Bundle.TYPE_KEY).isMissingNode());
        assertFalse(rootNode.path(Bundle.DATA_KEY).isMissingNode());
        assertFalse(rootNode.path(Bundle.REL_KEY).isMissingNode());
        assertEquals("one", rootNode.path(Bundle.DATA_KEY)
                .path("testarray").path(0).asText());
    }
}
