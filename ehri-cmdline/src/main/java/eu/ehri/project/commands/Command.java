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
import org.apache.commons.cli.CommandLine;

public interface Command {
    /**
     * Get information about the functionality of the command.
     *
     * @return a help text
     */
    public String getHelp();

    /**
     * Get information about the usage of the command, including
     * required and/or optional parameters.
     * @return a usage text
     */
    public String getUsage();

    /**
     * Execute this command with the given command line options.
     * @param graph
     * @param cmdLine
     * @return a status code (0 = success)
     * @throws Exception
     */
    public int execWithOptions(final FramedGraph<? extends TransactionalGraph> graph, CommandLine cmdLine) throws Exception;
    public int exec(FramedGraph<? extends TransactionalGraph> graph, String[] args) throws Exception;
}
