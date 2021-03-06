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

package eu.ehri.project.importers.cvoc;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.util.iterator.Filter;
import com.hp.hpl.jena.util.iterator.Map1;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.EaImporter;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.importers.util.Helpers;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.cvoc.AuthoritativeItem;
import eu.ehri.project.models.cvoc.AuthoritativeSet;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.ActionManager;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.BundleDAO;
import eu.ehri.project.persistence.Mutation;
import eu.ehri.project.views.impl.CrudViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public final class JenaSkosImporter implements SkosImporter {

    private static final Logger logger = LoggerFactory.getLogger(JenaSkosImporter.class);
    private final FramedGraph<? extends TransactionalGraph> framedGraph;
    private final Actioner actioner;
    private final Vocabulary vocabulary;
    private final BundleDAO dao;
    private final boolean tolerant;
    private final String format;
    private final String defaultLang;
    public static final String DEFAULT_LANG = "eng";
    // Language-sensitive properties.
    private static void addToMap(Map<String, Set<URI>> map, String key, URI value){
        if(map.containsKey(key)){
            map.get(key).add(value);
        }else{
            Set<URI> uris = new HashSet<URI>();
            uris.add(value);
            map.put(key, uris);
        }
    }
    public static final Map<String, Set<URI>> LANGUAGE_PROPS = new HashMap<String, Set<URI>>();
//    = ImmutableMap.<String, Set<URI>>builder()
    static {
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_ALTLABEL, SkosRDFVocabulary.ALT_LABEL.getURI());
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_HIDDENLABEL, SkosRDFVocabulary.HIDDEN_LABEL.getURI());
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_DEFINITION, SkosRDFVocabulary.DEFINITION.getURI());
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_SCOPENOTE, SkosRDFVocabulary.SCOPE_NOTE.getURI());
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_SCOPENOTE, URI.create("http://www.w3.org/2000/01/rdf-schema#comment"));
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_NOTE, SkosRDFVocabulary.NOTE.getURI());
            addToMap(LANGUAGE_PROPS,Ontology.CONCEPT_EDITORIAL_NOTE, SkosRDFVocabulary.EDITORIAL_NOTE.getURI());
    }

    // Language-agnostic properties.
    public static final Map<String, URI> GENERAL_PROPS = ImmutableMap.<String, URI>builder()
            .put("latitude", URI.create("http://www.w3.org/2003/01/geo/wgs84_pos#lat"))
            .put("longitude", URI.create("http://www.w3.org/2003/01/geo/wgs84_pos#long"))
            .put("latitude/longitude", URI.create("http://www.w3.org/2003/01/geo/wgs84_pos#lat_long"))
            .put("url", URI.create("http://xmlns.com/foaf/0.1/isPrimaryTopicOf"))
            .put("date", URI.create("http://semanticweb.cs.vu.nl/2009/11/sem/hasTime"))
            .put("personAccess", URI.create("http://semanticweb.cs.vu.nl/2009/11/sem/hasActor"))
            .put("placeAccess", URI.create("http://semanticweb.cs.vu.nl/2009/11/sem/hasPlace"))
            .build();
    // Properties that end up as undeterminedRelation nodes.
    public static final Map<String, URI> RELATION_PROPS = ImmutableMap.<String, URI>builder()
            .put("owl:sameAs", URI.create("http://www.w3.org/2002/07/owl#sameAs"))
            .put("skos:exactMatch", URI.create("http://www.w3.org/2004/02/skos/core#exactMatch"))
            .put("skos:closeMatch", URI.create("http://www.w3.org/2004/02/skos/core#closeMatch"))
            .put("skos:broadMatch", URI.create("http://www.w3.org/2004/02/skos/core#broadMatch"))
            .put("skos:relatedMatch", URI.create("http://www.w3.org/2004/02/skos/core#relatedMatch"))
            .put("sem:personAccess", URI.create("http://semanticweb.cs.vu.nl/2009/11/sem/hasActor"))
            .put("sem:placeAccess", URI.create("http://semanticweb.cs.vu.nl/2009/11/sem/hasPlace"))
            .build();

    /**
     * Constructor
     *
     * @param framedGraph The framed graph
     * @param actioner The actioner
     * @param vocabulary The target vocabulary
     * @param tolerant Whether or not to ignore single item validation errors.
     * @param format The RDF format
     * @param defaultLang The language to use for elements without specified language
     */
    public JenaSkosImporter(FramedGraph<? extends TransactionalGraph> framedGraph, Actioner actioner,
            Vocabulary vocabulary, boolean tolerant, String format, String defaultLang) {
        this.framedGraph = framedGraph;
        this.actioner = actioner;
        this.vocabulary = vocabulary;
        this.tolerant = tolerant;
        this.format = format;
        this.defaultLang = defaultLang;
        this.dao = new BundleDAO(framedGraph, vocabulary.idPath());
    }

    /**
     * Constructor
     *
     * @param framedGraph The framed graph
     * @param actioner The actioner
     * @param vocabulary The target vocabulary
     */
    public JenaSkosImporter(FramedGraph<? extends TransactionalGraph> framedGraph, Actioner actioner,
            Vocabulary vocabulary) {
        this(framedGraph, actioner, vocabulary, false, null, DEFAULT_LANG);
    }

    @Override
    public JenaSkosImporter setTolerant(boolean tolerant) {
        logger.debug("Setting importer to tolerant: " + tolerant);
        return new JenaSkosImporter(
                framedGraph, actioner, vocabulary, tolerant, format, defaultLang);
    }

    @Override
    public JenaSkosImporter setFormat(String format) {
        return new JenaSkosImporter(
                framedGraph, actioner, vocabulary, tolerant, format, defaultLang);
    }

    @Override
    public JenaSkosImporter setDefaultLang(String lang) {
        return new JenaSkosImporter(
                framedGraph, actioner, vocabulary, tolerant, format,
                Helpers.iso639DashTwoCode(lang));
    }

    /**
     * Import a file by path.
     *
     * @param filePath The SKOS file path
     * @param logMessage A log message
     * @return A log of imported nodes
     * @throws IOException
     * @throws ValidationError
     */
    @Override
    public ImportLog importFile(String filePath, String logMessage)
            throws IOException, ValidationError {
        FileInputStream ios = new FileInputStream(filePath);
        try {
            return importFile(ios, logMessage);
        } finally {
            ios.close();
        }
    }

    /**
     * Import an input stream.
     *
     * @param ios The SKOS file input stream
     * @param logMessage A log message
     * @return A log of imported nodes
     * @throws IOException
     * @throws ValidationError
     */
    @Override
    public ImportLog importFile(InputStream ios, String logMessage)
            throws IOException, ValidationError {
        try {
            // Create a new action for this import
            final ActionManager.EventContext eventContext = new ActionManager(framedGraph, vocabulary).logEvent(
                    actioner, EventTypes.ingest, getLogMessage(logMessage));
            // Create a manifest to store the results of the import.
            final ImportLog log = new ImportLog(eventContext);

            OntModel model = ModelFactory.createOntologyModel();
            model.read(ios, null, format);
            OntClass conceptClass = model.getOntClass(SkosRDFVocabulary.CONCEPT.getURI().toString());
            logger.debug("in import file: " + SkosRDFVocabulary.CONCEPT.getURI().toString());
            ExtendedIterator<? extends OntResource> extendedIterator = conceptClass.listInstances();
            Map<Resource, Concept> imported = Maps.newHashMap();

            while (extendedIterator.hasNext()) {
                Resource item = extendedIterator.next();

                try {
                    Mutation<Concept> graphConcept = importConcept(item);
                    imported.put(item, graphConcept.getNode());

                    switch (graphConcept.getState()) {
                        case UNCHANGED:
                            log.addUnchanged();
                            break;
                        case CREATED:
                            log.addCreated();
                            eventContext.addSubjects(graphConcept.getNode());
                            break;
                        case UPDATED:
                            log.addUpdated();
                            eventContext.addSubjects(graphConcept.getNode());
                            break;
                    }
                } catch (ValidationError validationError) {
                    if (tolerant) {
                        logger.error(validationError.getMessage());
                        log.setErrored(item.toString(), validationError.getMessage());
                    } else {
                        throw validationError;
                    }
                }
            }

            for (Map.Entry<Resource, Concept> pair : imported.entrySet()) {
                hookupRelationships(pair.getKey(), pair.getValue(), imported);
            }

            for (Concept concept : imported.values()) {
                vocabulary.addItem(concept);
                concept.setPermissionScope(vocabulary);
            }

            if (log.hasDoneWork()) {
                framedGraph.getBaseGraph().commit();
            } else {
                framedGraph.getBaseGraph().rollback();
            }

            return log;
        } catch (ValidationError error) {
            framedGraph.getBaseGraph().rollback();
            throw error;
        }
    }

    private Mutation<Concept> importConcept(Resource item) throws ValidationError {
        logger.debug("Importing: {}", item.toString());
        Bundle.Builder builder = Bundle.Builder.withClass(EntityClass.CVOC_CONCEPT)
                .addDataValue(Ontology.IDENTIFIER_KEY, getId(URI.create(item.getURI())));

        Map<AuthoritativeItem, String> linkedConcepts = new HashMap<AuthoritativeItem, String>();

        List<Bundle> undetermined = getUndeterminedRelations(item, linkedConcepts);
        for (Bundle description : getDescriptions(item)) {
            Bundle withRels = description
                    .withRelations(Ontology.HAS_ACCESS_POINT, undetermined);
            builder.addRelation(Ontology.DESCRIPTION_FOR_ENTITY, withRels);
        }

        Mutation<Concept> mut = dao.createOrUpdate(builder.build(), Concept.class);
        solveUndeterminedRelationships(mut.getNode(), linkedConcepts);
        return mut;
    }

    private void solveUndeterminedRelationships(Concept unit, Map<AuthoritativeItem, String> linkedConcepts) {
        GraphManager manager = GraphManagerFactory.getInstance(framedGraph);
        for (AuthoritativeItem concept : linkedConcepts.keySet()) {
            try {
                String reltype = linkedConcepts.get(concept);
                Bundle linkBundle = new Bundle(EntityClass.LINK)
                        .withDataValue(Ontology.LINK_HAS_TYPE, "associate")
                        .withDataValue(reltype.substring(0, reltype.indexOf(":")), reltype.substring(reltype.indexOf(":")+1))
                        .withDataValue(Ontology.LINK_HAS_DESCRIPTION, EaImporter.RESOLVED_LINK_DESC);
                UserProfile user = manager.getFrame(actioner.getId(), UserProfile.class);
                Link link;
                link = new CrudViews<Link>(framedGraph, Link.class).create(linkBundle, user);
                unit.addLink(link);
                concept.addLink(link);
            } catch (ItemNotFound ex) {
                logger.error(ex.getMessage());
            } catch (PermissionDenied ex) {
                logger.error(ex.getMessage());
            } catch (ValidationError ex) {
                logger.error(ex.getMessage());
            }

        }
    }

    private List<Bundle> getUndeterminedRelations(Resource item, Map<AuthoritativeItem, String> linkedItems) {
        List<Bundle> undetermined = Lists.newArrayList();

        for (Map.Entry<String, URI> rel : RELATION_PROPS.entrySet()) {
            for (RDFNode annotation : getObjectWithPredicate(item, rel.getValue())) {
                if (annotation.isLiteral()) {
                    undetermined.add(new Bundle(EntityClass.UNDETERMINED_RELATIONSHIP)
                            .withDataValue(Ontology.ANNOTATION_TYPE, rel.getKey())
                            .withDataValue(Ontology.NAME_KEY, annotation.toString()));
                } else {
                    if (rel.getKey().startsWith("skos:") || rel.getKey().startsWith("sem:") ) {
                        String prefix = rel.getKey().startsWith("skos:") ? "skos" : "sem";
                        AuthoritativeItem found = findRelatedConcept(annotation.toString());
                        if (found != null) {
                            linkedItems.put(found, rel.getKey());
                        } else {
                            undetermined.add(new Bundle(EntityClass.UNDETERMINED_RELATIONSHIP)
                                    .withDataValue(Ontology.ANNOTATION_TYPE, "associate")
                                    .withDataValue(prefix, rel.getKey().substring(rel.getKey().indexOf(":")+1))
                                    .withDataValue(Ontology.NAME_KEY, annotation.toString()));
                        }
                    }
                }
            }
        }
        return undetermined;
    }

    private AuthoritativeItem findRelatedConcept(String name) {
        if (name != null) {
            String[] domains = name.split("/");
            if (domains.length > 2) {
                String cvoc_id = domains[domains.length - 2];
                String concept_id = domains[domains.length - 1];
                AuthoritativeSet referredSet;
                try {
                    GraphManager manager = GraphManagerFactory.getInstance(framedGraph);
                    referredSet = manager.getFrame(cvoc_id, AuthoritativeSet.class);
                    for (AuthoritativeItem authItem : referredSet.getAuthoritativeItems()) {
                        if (authItem.getIdentifier().equals(concept_id)) {
                            return authItem;
                        }
                    }
                } catch (ItemNotFound ex) {
                    logger.error("AuthoritativeSet with id " + cvoc_id + " not found. " + ex.getMessage());
                }
            }
        }
        return null;
    }

    private static interface ConnectFunc {

        public void connect(Concept current, Concept related);
    }

    private List<RDFNode> getObjectWithPredicate(final Resource item, final URI propUri) {
        // NB: this should be possible with simply item.listProperties(propUri)
        // but for some reason that doesn't work... I can't grok why.
        return item.listProperties().filterKeep(new Filter<Statement>() {
            @Override
            public boolean accept(Statement statement) {
                return statement.getPredicate().hasURI(propUri.toString());
            }
        }).mapWith(new Map1<Statement, RDFNode>() {
            @Override
            public RDFNode map1(Statement statement) {
                return statement.getObject();
            }
        }).toList();
    }

    private void connectRelation(Concept current, Resource item, Map<Resource, Concept> others,
            URI propUri, ConnectFunc connectFunc) {
        for (RDFNode other : getObjectWithPredicate(item, propUri)) {
            if (other.isResource()) {
                Concept related = others.get(other.asResource());
                if (related != null) {
                    connectFunc.connect(current, related);
                }
            }
        }
    }

    private void hookupRelationships(Resource item, Concept current,
            Map<Resource, Concept> conceptMap) {

        connectRelation(current, item, conceptMap, SkosRDFVocabulary.BROADER.getURI(), new ConnectFunc() {
            @Override
            public void connect(Concept current, Concept related) {
                related.addNarrowerConcept(current);
            }
        });

        connectRelation(current, item, conceptMap, SkosRDFVocabulary.NARROWER.getURI(), new ConnectFunc() {
            @Override
            public void connect(Concept current, Concept related) {
                current.addNarrowerConcept(related);
            }
        });

        connectRelation(current, item, conceptMap, SkosRDFVocabulary.RELATED.getURI(), new ConnectFunc() {
            @Override
            public void connect(Concept current, Concept related) {
                current.addRelatedConcept(related);
            }
        });
    }

    private List<Bundle> getDescriptions(Resource item) {
        List<Bundle> descriptions = Lists.newArrayList();

        for (RDFNode property : getObjectWithPredicate(item, SkosRDFVocabulary.PREF_LABEL.getURI())) {
            if (!property.isLiteral()) {
                continue;
            }

            Bundle.Builder builder = Bundle.Builder.withClass(EntityClass.CVOC_CONCEPT_DESCRIPTION);

            Literal literalPrefName = property.asLiteral();
            String languageCode = isValidLanguageCode(literalPrefName.getLanguage())
                    ? Helpers.iso639DashTwoCode(literalPrefName.getLanguage())
                    : defaultLang;

            builder.addDataValue(Ontology.NAME_KEY, literalPrefName.getString())
                    .addDataValue(Ontology.LANGUAGE, languageCode);

            for (Map.Entry<String, URI> prop : GENERAL_PROPS.entrySet()) {
                for (RDFNode target : getObjectWithPredicate(item, prop.getValue())) {
                    if (target.isLiteral()) {
                        if (prop.getKey().equals("latitude/longitude")) {
                            String[] latlong = target.asLiteral().getString().split(",");
                            if (latlong.length > 1) {
                                builder.addDataValue("latitude", latlong[0]);
                                builder.addDataValue("longitude", latlong[1]);
                            }
                        } else {
                            builder.addDataValue(prop.getKey(), target.asLiteral().getString());
                        }
                    } else {
                        builder.addDataValue(prop.getKey(), target.toString());
                    }
                }
            }

            for (Map.Entry<String, Set<URI>> prop : LANGUAGE_PROPS.entrySet()) {
                List<String> values = Lists.newArrayList();
                for (URI uri : prop.getValue()) {
                    for (RDFNode target : getObjectWithPredicate(item, uri)) {
                        if (target.isLiteral()) {
                            Literal literal = target.asLiteral();
                            String propLanguageCode = isValidLanguageCode(literal.getLanguage())
                                    ? Helpers.iso639DashTwoCode(literal.getLanguage())
                                    : defaultLang;
                            if (propLanguageCode.equals(languageCode)) {
                                values.add(literal.getString());
                            }
                        }
                    }
                }
                if (!values.isEmpty()) {
                    builder.addDataValue(prop.getKey(), values);
                }
            }
            Bundle bundle = builder.build();
            logger.trace(bundle.toJson());
            descriptions.add(bundle);
        }

        return descriptions;
    }

    private String getId(URI uri) {
        return uri.getPath().substring(uri.getPath().lastIndexOf("/") + 1)
                + (uri.getQuery() != null ? uri.getQuery() : "")
                + (uri.getFragment() != null ? uri.getFragment() : "");
    }

    private Optional<String> getLogMessage(String msg) {
        return msg.trim().isEmpty() ? Optional.<String>absent() : Optional.of(msg);
    }

    private boolean isValidLanguageCode(String language) {
        return !(language == null || language.isEmpty());
    }
}
