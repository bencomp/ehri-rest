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

package eu.ehri.project.persistence;

import eu.ehri.project.models.base.Frame;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test the subtree traverser actually works.
 * 
 * @author Mike Bryant (http://github.com/mikesname)
 *
 */
public class SubtreeTraverserTest extends AbstractFixtureTest {

    private static class Counter {
        public int count = 0;
    }

    @Test
    public void testSubtreeSerializationTouchesAllNodes() throws Exception {
        // Doc c1 has six nodes in its subtree
        final Counter counter = new Counter();
        new Serializer.Builder(graph).dependentOnly().build()
                .traverseSubtree(item, new TraversalCallback() {
            public void process(Frame vertexFrame, int depth, String rname, int rnum) {
                counter.count++;
            }
        });
        assertEquals(6, counter.count);
    }
}
