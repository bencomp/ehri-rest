package eu.ehri.project.commands;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.persistence.Serializer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;

/**
 * Import EAD from the command line...
 */
public class ListEntities extends BaseCommand implements Command {

    final static String NAME = "list";

    /**
     * Constructor.
     */
    public ListEntities() {
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void setCustomOptions() {
        options.addOption(OptionBuilder
                .withType(String.class)
                .withLongOpt("format").isRequired(false)
                .hasArg(true).withArgName("f")
                .withDescription("Format for output data, which defaults to just the id. " +
                        "Currently only JSON is supported: json").create("f"));
        options.addOption(OptionBuilder
                .withType(String.class)
                .withLongOpt("root-node").isRequired(false)
                .hasArg(true).withArgName("r")
                .withDescription("Name of the root node (default: '" + NAME + "')").create("r"));
    }

    @Override
    public String getHelp() {
        return "Usage: list [OPTIONS] <type>";
    }

    @Override
    public String getUsage() {
        String help = "List entities of a given type.";
        return help;
    }

    /**
     * Command-line entry-point (for testing.)
     *
     * @throws Exception
     */
    @Override
    public int execWithOptions(final FramedGraph<? extends TransactionalGraph> graph, CommandLine cmdLine) throws Exception {

        // the first argument is the entity type, and that must be specified
        if (cmdLine.getArgList().size() < 1)
            throw new RuntimeException(getHelp());
        EntityClass type = EntityClass.withName(cmdLine.getArgs()[0]);
        Class<?> cls = type.getEntityClass();

        if (!AccessibleEntity.class.isAssignableFrom(cls))
            throw new RuntimeException("Unknown accessible entity: " + type);

        GraphManager manager = GraphManagerFactory.getInstance(graph);
        Serializer serializer = new Serializer(graph);
        String rootName = cmdLine.getOptionValue("r", NAME);

        if (!cmdLine.hasOption("f")) {
            // default to only outputting the id's
            printIds(manager, type);
        } else {
            // if there is a second argument, that might be 'json' or 'xml'
            String format = cmdLine.getOptionValue("f");
            if (format.equalsIgnoreCase("json")) {
                printJson(manager, serializer, type);
            } else {
                throw new RuntimeException("Unknown format: " + cmdLine.getOptionValue("f") + " (currently only " +
                        "'json' is supported)");
            }
        }

        return 0;
    }

    /**
     * Output node IDs only.
     *
     * @param manager The manager
     * @param type The entity class
     */
    private void printIds(GraphManager manager, EntityClass type) {
        for (AccessibleEntity acc : manager.getFrames(type, AccessibleEntity.class)) {
            System.out.println(acc.getId());
        }
    }

    /**
     * Output nodes as JSON.
     *
     * @param manager The manager
     * @param serializer The serializer
     * @param type The entity class
     * @throws SerializationError
     */
    private void printJson(GraphManager manager, Serializer serializer, EntityClass type)
            throws SerializationError {

        // NOTE no json root {}, but always a list []
        System.out.print("[\n");

        int cnt = 0;
        for (AccessibleEntity acc : manager.getFrames(type, AccessibleEntity.class)) {
            String jsonString = serializer.vertexFrameToJson(acc);
            if (cnt != 0) System.out.print(",\n");
            System.out.println(jsonString);
            cnt++;
        }
        System.out.print("]\n"); // end list

    }
}