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

package eu.ehri.project.views;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.models.DocumentDescription;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.HistoricalAgent;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.LinkableEntity;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class LinkViewsTest extends AbstractFixtureTest {

    private LinkViews linkViews;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        linkViews = new LinkViews(graph);
    }

    @Test
    public void testLinks() throws Exception {
        Link link1 = manager.getFrame("link1", Link.class);
        List<LinkableEntity> targets = Lists.newArrayList(link1.getLinkTargets());
        assertTrue(targets.contains(manager.getFrame("c1", LinkableEntity.class)));
        assertTrue(targets.contains(manager.getFrame("c4", LinkableEntity.class)));
    }

    @Test
    public void testCreateLink() throws Exception {
        DocumentaryUnit src = manager.getFrame("c1", DocumentaryUnit.class);
        HistoricalAgent dst = manager.getFrame("a1", HistoricalAgent.class);
        UndeterminedRelationship rel = manager.getFrame("ur1", UndeterminedRelationship.class);
        String linkDesc = "Test Link";
        String linkType = "subjectAccess";
        Bundle linkBundle = getLinkBundle(linkDesc, linkType);
        Link link = linkViews.createLink("c1", "a1", Lists.newArrayList("ur1"),
                linkBundle, validUser);
        assertEquals(linkDesc, link.getDescription());
        assertEquals(2L, Iterables.size(link.getLinkTargets()));
        assertTrue(Iterables.contains(link.getLinkTargets(), src));
        assertTrue(Iterables.contains(link.getLinkTargets(), dst));
        assertEquals(1L, Iterables.size(link.getLinkBodies()));
        assertTrue(Iterables.contains(link.getLinkBodies(), rel));
    }


    @Test(expected = PermissionDenied.class)
    public void testCreateLinkWithoutPermission() throws Exception {
        linkViews.createLink("c1", "a1", Lists.newArrayList("ur1"),
                getLinkBundle("won't work!", "too bad!"), invalidUser);
    }

    @Test
    public void testCreateAccessPointLink() throws Exception {
        DocumentaryUnit src = manager.getFrame("c1", DocumentaryUnit.class);
        HistoricalAgent dst = manager.getFrame("a1", HistoricalAgent.class);
        DocumentDescription desc = manager.getFrame("cd1", DocumentDescription.class);
        String linkDesc = "Test Link";
        String linkType = "subjectAccess";
        Bundle linkBundle = getLinkBundle(linkDesc, linkType);
        Link link = linkViews.createAccessPointLink("c1", "a1", "cd1", linkDesc, linkType,
                linkBundle, validUser);
        assertEquals(linkDesc, link.getDescription());
        assertEquals(2L, Iterables.size(link.getLinkTargets()));
        assertTrue(Iterables.contains(link.getLinkTargets(), src));
        assertTrue(Iterables.contains(link.getLinkTargets(), dst));
        assertEquals(1L, Iterables.size(link.getLinkBodies()));
        UndeterminedRelationship rel = manager.cast(link.getLinkBodies().iterator().next(),
                UndeterminedRelationship.class);
        assertEquals(rel.getName(), linkDesc);
        assertEquals(rel.getRelationshipType(), linkType);
        Description d = rel.getDescription();
        assertEquals(desc, d);
    }

    private Bundle getLinkBundle(String linkDesc, String linkType) {
        return Bundle.Builder.withClass(EntityClass.LINK)
                .addDataValue(Ontology.LINK_HAS_TYPE, linkType)
                .addDataValue(Ontology.LINK_HAS_DESCRIPTION, linkDesc)
                .build();
    }
}