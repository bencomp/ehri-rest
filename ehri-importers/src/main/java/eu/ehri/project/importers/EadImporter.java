package eu.ehri.project.importers;

import com.google.common.collect.Sets;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.*;
import eu.ehri.project.models.base.*;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.Bundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.ehri.project.persistence.BundleDAO;
import eu.ehri.project.persistence.Mutation;
import eu.ehri.project.persistence.Serializer;
import eu.ehri.project.views.impl.CrudViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import EAD for a given repository into the database. Due to the laxness of the EAD standard this is a fairly complex
 * procedure. An EAD a single entity at the highest level of description or multiple top-level entities, with or without
 * a hierarchical structure describing their child items. This means that we need to recursively descend through the
 * archdesc and c,c01-12 levels.
 *
 * TODO: Extensive cleanups, optimisation, and rationalisation.
 *
 * @author lindar
 *
 */
public class EadImporter extends EaImporter {

    private static final Logger logger = LoggerFactory.getLogger(EadImporter.class);
    //the EadImporter can import ead as DocumentaryUnits, the default, or overwrite those and create VirtualUnits instead.
    private EntityClass unitEntity = EntityClass.DOCUMENTARY_UNIT;
    private Serializer mergeSerializer;
    /**
     * Construct an EadImporter object.
     *
     * @param framedGraph
     * @param permissionScope
     * @param log
     */
    public EadImporter(FramedGraph<?> framedGraph, PermissionScope permissionScope, ImportLog log) {
        super(framedGraph, permissionScope, log);
        mergeSerializer = new Serializer.Builder(framedGraph).dependentOnly().build();
    }

    /**
     * Import a single archdesc or c01-12 item, keeping a reference to the hierarchical depth.
     *
     * @param itemData The data map
     * @param idPath The identifiers of parent documents,
     *               not including those of the overall permission scope
     * @throws ValidationError when the itemData does not contain an identifier for the unit or...
     */
    @Override
    public AbstractUnit importItem(Map<String, Object> itemData, List<String> idPath)
            throws ValidationError {

        BundleDAO persister = getPersister(idPath);

        // extractDocumentaryUnit does not throw ValidationError on missing ID
        Bundle unit = new Bundle(unitEntity, extractDocumentaryUnit(itemData));
        
        // Check for missing identifier, throw an exception when there is no ID.
        if (unit.getDataValue(Ontology.IDENTIFIER_KEY) == null) {
            throw new ValidationError(unit, Ontology.IDENTIFIER_KEY,
                    "Missing identifier " + Ontology.IDENTIFIER_KEY);
        }
        logger.debug("Imported item: " + itemData.get("name"));
        Bundle descBundle = new Bundle(EntityClass.DOCUMENT_DESCRIPTION, extractUnitDescription(itemData, EntityClass.DOCUMENT_DESCRIPTION));
        // Add dates and descriptions to the bundle since they're @Dependent
        // relations.
        for (Map<String, Object> dpb : extractDates(itemData)) {
            descBundle = descBundle.withRelation(Ontology.ENTITY_HAS_DATE, new Bundle(EntityClass.DATE_PERIOD, dpb));
        }
        for (Map<String, Object> rel : extractRelations(itemData)) {//, (String) unit.getErrors().get(IdentifiableEntity.IDENTIFIER_KEY)
            logger.debug("relation found: " + rel.get(Ontology.NAME_KEY));
            descBundle = descBundle.withRelation(Ontology.HAS_ACCESS_POINT, new Bundle(EntityClass.UNDETERMINED_RELATIONSHIP, rel));
        }
        Map<String, Object> unknowns = extractUnknownProperties(itemData);
        if (!unknowns.isEmpty()) {
            logger.debug("Unknown Properties found");
            descBundle = descBundle.withRelation(Ontology.HAS_UNKNOWN_PROPERTY, new Bundle(EntityClass.UNKNOWN_PROPERTY, unknowns));
        }
        for (Map<String, Object> dpb : extractMaintenanceEvent(itemData)) {
            logger.debug("maintenance event found");
            //dates in maintenanceEvents are no DatePeriods, they are not something to search on
            descBundle = descBundle.withRelation(Ontology.HAS_MAINTENANCE_EVENT, new Bundle(EntityClass.MAINTENANCE_EVENT, dpb));
        }

        Mutation<DocumentaryUnit> mutation =
                persister.createOrUpdate(mergeWithPreviousAndSave(unit, descBundle, idPath), DocumentaryUnit.class);
        DocumentaryUnit frame = mutation.getNode();

        // Set the repository/item relationship
        if (idPath.isEmpty() && mutation.created()) {
            EntityClass scopeType = manager.getEntityClass(permissionScope);
            if (scopeType.equals(EntityClass.REPOSITORY)) {
                Repository repository = framedGraph.frame(permissionScope.asVertex(), Repository.class);
                frame.setRepository(repository);
                frame.setPermissionScope(repository);
            } else if (scopeType.equals(unitEntity)) {
                DocumentaryUnit parent = framedGraph.frame(permissionScope.asVertex(), DocumentaryUnit.class);
                parent.addChild(frame);
                frame.setPermissionScope(parent);
            } else {
                logger.error("Unknown scope type for documentary unit: {}", scopeType);
            }
        }
        handleCallbacks(mutation);
        if (mutation.created()) {
            solveUndeterminedRelationships(frame, descBundle);
        }
        return frame;


    }
/**
     * finds any bundle in the graph with the same ObjectIdentifier.
     * if it exists it replaces the Description in the given language, else it just saves it
     *
     * @param unit       - the DocumentaryUnit to be saved
     * @param descBundle - the documentsDescription to replace any previous ones with this language
     * @return A bundle with description relationships merged.
     * @throws ValidationError
     */

    protected Bundle mergeWithPreviousAndSave(Bundle unit, Bundle descBundle, List<String> idPath) throws ValidationError {
        final String languageOfDesc = descBundle.getDataValue(Ontology.LANGUAGE_OF_DESCRIPTION);
        /*
         * for some reason, the idpath from the permissionscope does not contain the parent documentary unit.
         * TODO: so for now, it is added manually
         */
        List<String> lpath = new ArrayList<String>();
        for(String p : getPermissionScope().idPath()){
            lpath.add(p);
        }
        for(String p : idPath){
            lpath.add(p);
        }
        Bundle withIds = unit.generateIds(lpath);
        
        
        logger.debug("idpath: "+withIds.getId());
        if (manager.exists(withIds.getId())) {
            try {
                //read the current item’s bundle
                Bundle oldBundle = mergeSerializer
                        .vertexFrameToBundle(manager.getVertex(withIds.getId()));

                //filter out dependents that a) are descriptions, b) have the same language/code
                Bundle.Filter filter = new Bundle.Filter() {
                    @Override
                    public boolean remove(String relationLabel, Bundle bundle) {
                        String lang = bundle.getDataValue(Ontology.LANGUAGE);
                        return bundle.getType().equals(EntityClass.DOCUMENT_DESCRIPTION)
                                && (lang != null
                                && lang.equals(languageOfDesc));
                    }
                };
                Bundle filtered = oldBundle.filterRelations(filter);

                return withIds.withRelations(filtered.getRelations())
                        .withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);

            } catch (SerializationError ex) {
                throw new ValidationError(unit, "serialization error", ex.getMessage());
            } catch (ItemNotFound ex) {
                throw new ValidationError(unit, "item not found exception", ex.getMessage());
            }
        } else {
            return unit.withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);
        }
    }

    /**
     * subclasses can override this method to cater to their special needs for UndeterminedRelationships
     * by default, it expects something like this in the original EAD:
     * 
     * <persname source="terezin-victims" authfilenumber="PERSON.ITI.1514982">Kien,
                        Leonhard (* 11.5.1886)</persname>
     *
     * it works in unison with the extractRelations() method. 
     * 
                        * 
     * @param unit
     * @param descBundle - not used
     * @throws ValidationError 
     */
    protected void solveUndeterminedRelationships(DocumentaryUnit unit, Bundle descBundle) throws ValidationError {
        //Try to resolve the undetermined relationships
        //we can only create the annotations after the DocumentaryUnit and its Description have been added to the graph,
        //so they have id's. 
        for (Description unitdesc : unit.getDescriptions()) {
            // Put the set of relationships into a HashSet to remove duplicates.
            for (UndeterminedRelationship rel : Sets.newHashSet(unitdesc.getUndeterminedRelationships())) {
                /*
                 * the wp2 undetermined relationship that can be resolved have a 'cvoc' and a 'concept' attribute.
                 * they need to be found in the vocabularies that are in the graph
                 */
                if (rel.asVertex().getPropertyKeys().contains("cvoc")) {
                    String cvoc_id = (String) rel.asVertex().getProperty("cvoc");
                    String concept_id = (String) rel.asVertex().getProperty("concept");
                    logger.debug(cvoc_id + "  " + concept_id);
                    Vocabulary vocabulary;
                    try {
                        vocabulary = manager.getFrame(cvoc_id, Vocabulary.class);
                        for (Concept concept : vocabulary.getConcepts()) {
                        logger.debug("*********************" + concept.getId() + " " + concept.getIdentifier());
                        if (concept.getIdentifier().equals(concept_id)) {
                            try {
                                Bundle linkBundle = new Bundle(EntityClass.LINK)
                                        .withDataValue(Ontology.LINK_HAS_TYPE, rel.asVertex().getProperty("type").toString())
                                        .withDataValue(Ontology.LINK_HAS_DESCRIPTION, "solved by automatic resolving");
                                UserProfile user = manager.getFrame(this.log.getActioner().getId(), UserProfile.class);
                                Link link = new CrudViews<Link>(framedGraph, Link.class).create(linkBundle, user);
                                unit.addLink(link);
                                concept.addLink(link);
                                link.addLinkBody(rel);
                            } catch (PermissionDenied ex) {
                                logger.error(ex.getMessage());
                            } catch (IntegrityError ex) {
                                logger.error(ex.getMessage());
                            }

                        }

                    }
                    } catch (ItemNotFound ex) {
                        logger.error("Vocabulary with id " + cvoc_id +" not found. "+ex.getMessage());
                    }
                    
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
   @Override
    protected Iterable<Map<String, Object>> extractRelations(Map<String, Object> data) {
        final String REL = "AccessPoint";
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (String key : data.keySet()) {
            if (key.endsWith(REL)) {
                //type, targetUrl, targetName, notes
                for (Map<String, Object> origRelation : (List<Map<String, Object>>) data.get(key)) {
                    Map<String, Object> relationNode = new HashMap<String, Object>();
                    for (String eventkey : origRelation.keySet()) {
//                        logger.debug(eventkey);
                        if (eventkey.endsWith(REL)) {
                            relationNode.put(Ontology.UNDETERMINED_RELATIONSHIP_TYPE, eventkey.substring(0, eventkey.indexOf("Point")));
                            relationNode.put(Ontology.NAME_KEY, origRelation.get(eventkey));
logger.debug("------------------" + eventkey.substring(0, eventkey.indexOf("Point")) + ": "+ origRelation.get(eventkey));                            
                        } else {
                            relationNode.put(eventkey, origRelation.get(eventkey));
                        }
                    }
                    if (!relationNode.containsKey(Ontology.UNDETERMINED_RELATIONSHIP_TYPE)) {
                        relationNode.put(Ontology.UNDETERMINED_RELATIONSHIP_TYPE, "corporateBodyAccess");
                    }
                    list.add(relationNode);
                }
            }
        }
        return list;
    }

    /**
     * Creates a Map containing properties of a Documentary Unit.
     * These properties are the unit's identifiers.
     * @param itemData Map of all extracted information
     * @param depth depth of node in the tree
     * @return a Map representing a Documentary Unit node
     * @throws ValidationError
     */
    protected Map<String, Object> extractDocumentaryUnit(Map<String, Object> itemData, int depth) throws ValidationError {
        Map<String, Object> unit = new HashMap<String, Object>();
        if (itemData.get(OBJECT_ID) != null) {
            unit.put(Ontology.IDENTIFIER_KEY, itemData.get(OBJECT_ID));
        }
        if (itemData.get(Ontology.OTHER_IDENTIFIERS) != null) {
        	logger.debug("otherIdentifiers is not null");
            unit.put(Ontology.OTHER_IDENTIFIERS, itemData.get(Ontology.OTHER_IDENTIFIERS));
        }
        return unit;
    }
    
    /**
     * Creates a Map containing properties of a Documentary Unit.
     * These properties are the unit's identifiers.
     * @param itemData Map of all extracted information
     * @return a Map representing a Documentary Unit node
     * @throws ValidationError
     */
    @Override
    protected Map<String, Object> extractDocumentaryUnit(Map<String, Object> itemData) throws ValidationError {
        Map<String, Object> unit = new HashMap<String, Object>();
        if (itemData.get(OBJECT_ID) != null) {
            unit.put(Ontology.IDENTIFIER_KEY, itemData.get(OBJECT_ID));
        }
        if (itemData.get(Ontology.OTHER_IDENTIFIERS) != null) {
        	logger.debug("otherIdentifiers is not null");
            unit.put(Ontology.OTHER_IDENTIFIERS, itemData.get(Ontology.OTHER_IDENTIFIERS));
        }
        return unit;
    }

    /**
     * Creates a Map containing properties of a Documentary Unit description.
     * These properties are the unit description's properties: all except the doc unit identifiers and unknown properties.
     * @param itemData Map of all extracted information
     * @param depth depth of node in the tree
     * @return a Map representing a Documentary Unit Description node
     * @throws ValidationError
     */
    protected Map<String, Object> extractDocumentDescription(Map<String, Object> itemData, int depth) throws ValidationError {

        Map<String, Object> unit = new HashMap<String, Object>();
        for (String key : itemData.keySet()) {
            if (!(key.equals(OBJECT_ID) 
            	|| key.equals(Ontology.OTHER_IDENTIFIERS) 
            	|| key.startsWith(SaxXmlHandler.UNKNOWN))) {
                unit.put(key, itemData.get(key));
            }
        }
        return unit;
    }

    @Override
    public AccessibleEntity importItem(Map<String, Object> itemData) throws ValidationError {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    public void importAsVirtualCollection(){
      unitEntity = EntityClass.VIRTUAL_UNIT;
    }
}
