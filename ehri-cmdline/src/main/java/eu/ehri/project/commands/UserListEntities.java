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
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.views.Query;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

/**
 * Import EAD from the command line...
 * 
 */
public class UserListEntities extends BaseCommand implements Command {

    final static String NAME = "user-list";

    /**
     * Constructor.
     * 
     */
    public UserListEntities() {
    }

    @Override
    public String getHelp() {
        return "Usage: user-list [OPTIONS] <type>";
    }

    @Override
    public String getUsage() {
        return "List entities of a given type as a given user.";
    }
    
    @Override
    public void setCustomOptions() {
        options.addOption(new Option("user", true,
                "Identifier of user to list items as"));
    }

    @Override
    public int execWithOptions(final FramedGraph<? extends TransactionalGraph> graph,
            CommandLine cmdLine) throws Exception {

        if (cmdLine.getArgList().size() < 1)
            throw new RuntimeException(getHelp());

        GraphManager manager = GraphManagerFactory.getInstance(graph);
        EntityClass type = EntityClass.withName(cmdLine.getArgs()[0]);
        Class<?> cls = type.getEntityClass();

        if (!AccessibleEntity.class.isAssignableFrom(cls))
            throw new RuntimeException("Unknown accessible entity: " + type);

        UserProfile user = manager.getFrame(
                cmdLine.getOptionValue("user"), UserProfile.class);

        @SuppressWarnings("unchecked")
        Query<AccessibleEntity> query = new Query<AccessibleEntity>(graph,
                (Class<AccessibleEntity>) cls);
        for (AccessibleEntity acc : query.page(user)) {
            System.out.println(acc.getId());
        }

        return 0;
    }
}
