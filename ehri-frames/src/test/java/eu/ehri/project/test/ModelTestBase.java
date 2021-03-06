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

package eu.ehri.project.test;

import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.utils.fixtures.FixtureLoader;
import eu.ehri.project.utils.fixtures.FixtureLoaderFactory;
import eu.ehri.project.views.Crud;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class ModelTestBase extends GraphTestBase {

    protected Crud<DocumentaryUnit> views;
    protected FixtureLoader helper;

    protected <T> List<T> toList(Iterable<T> iter) {
        Iterator<T> it = iter.iterator();
        List<T> lst = new ArrayList<T>();
        while (it.hasNext())
            lst.add(it.next());
        return lst;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        helper = FixtureLoaderFactory.getInstance(graph);
        helper.loadTestData();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }


}
