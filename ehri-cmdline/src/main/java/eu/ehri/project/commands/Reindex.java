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

package eu.ehri.project.commands;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphReindexer;
import org.apache.commons.cli.CommandLine;

/**
 * Import EAD from the command line...
 * 
 */
public class Reindex extends BaseCommand implements Command {

    final static String NAME = "reindex";


    public Reindex() {
    }

    @Override
    protected void setCustomOptions() {
    }

    @Override
    public String getHelp() {
        return "Usage: reindex";
    }

    @Override
    public String getUsage() {
        return "Drop and rebuild the (internal) graph index.";
    }


    @Override
    public int execWithOptions(final FramedGraph<? extends TransactionalGraph> graph, CommandLine cmdLine) throws Exception {
        GraphReindexer reIndexer = new GraphReindexer(graph);
        reIndexer.reindex();
        return 0;
    }
}
