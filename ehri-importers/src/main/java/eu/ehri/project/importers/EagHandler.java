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

package eu.ehri.project.importers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.properties.XmlImportProperties;
import eu.ehri.project.models.MaintenanceEvent;
import eu.ehri.project.models.base.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * to be used in conjunction with EagImporter
 *
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
public class EagHandler extends SaxXmlHandler {

    private final ImmutableMap<String, Class<? extends Frame>> possibleSubnodes
            = ImmutableMap.<String, Class<? extends Frame>>builder().put(
            "maintenanceEvent", MaintenanceEvent.class).build();
    private static final Logger logger = LoggerFactory.getLogger(EagHandler.class);

    public EagHandler(AbstractImporter<Map<String, Object>> importer) {
        super(importer, new XmlImportProperties("eag.properties"));
    }

    @Override
    protected boolean needToCreateSubNode(String qName) {
        return possibleSubnodes.containsKey(getImportantPath(currentPath));
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        //if a subnode is ended, add it to the super-supergraph
        super.endElement(uri, localName, qName);

        if (needToCreateSubNode(qName)) {
            logger.debug("endElement: " + qName);

            logger.debug("just before popping: " + depth + "-" + getImportantPath(currentPath) + "-" + qName);
            Map<String, Object> currentGraph = currentGraphPath.pop();
            putSubGraphInCurrentGraph(getImportantPath(currentPath), currentGraph);
//            currentGraphPath.pop();
            depth--;
        }
        
        currentPath.pop();
        //an EAG file consists of only 1 element, so if we're back at the root, we're done
        if (currentPath.isEmpty()) {
            try {
                logger.debug("depth close " + depth + " " + qName);
                //TODO: add any mandatory fields not yet there:
                if (!currentGraphPath.peek().containsKey("objectIdentifier")) {
                    logger.warn("no objectIdentifier found");
                    putPropertyInCurrentGraph("objectIdentifier", "id");
                }
                if (!currentGraphPath.peek().containsKey("typeOfEntity")) {
                    putPropertyInCurrentGraph("typeOfEntity", "organisation");
                }
                if (!currentGraphPath.peek().containsKey(Ontology.NAME_KEY)) {
                    logger.debug("no " + Ontology.NAME_KEY + " found");
                    putPropertyInCurrentGraph(Ontology.NAME_KEY, "title");
                }
                if (!currentGraphPath.peek().containsKey(Ontology.LANGUAGE_OF_DESCRIPTION)) {
                    logger.debug("no " + Ontology.LANGUAGE_OF_DESCRIPTION + " found");
                    putPropertyInCurrentGraph(Ontology.LANGUAGE_OF_DESCRIPTION, "en");
                }
                if (!currentGraphPath.peek().containsKey("rulesAndConventions")) {
                    logger.debug("no rulesAndConventions found");
                    putPropertyInCurrentGraph("rulesAndConventions", "ISDIAH");
                }
                importer.importItem(currentGraphPath.pop(), Lists.<String>newArrayList());

            } catch (ValidationError ex) {
                logger.error(ex.getMessage());
            }
        }
    }
        @Override
    protected List<String> getSchemas() {
        List<String> schemas = new ArrayList<String>();
        schemas.add("eag.xsd");
        return schemas;
    }
}
