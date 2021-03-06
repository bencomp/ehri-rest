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

import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.views.impl.CrudViews;
import eu.ehri.project.views.impl.LoggingCrudViews;


/**
 * Factory for creating crud, with or without logging.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class ViewFactory {

    public static <E extends AccessibleEntity> Crud<E> getCrudNoLogging(FramedGraph<?> graph,
            Class<E> cls) {
        return new CrudViews<E>(graph, cls);
    }

    public static <E extends AccessibleEntity> Crud<E> getCrudWithLogging(FramedGraph<?> graph,
            Class<E> cls) {
        return new LoggingCrudViews<E>(graph, cls);
    }
}