package eu.ehri.project.importers;

import java.util.HashMap;
import java.util.Map;

import eu.ehri.project.models.events.Action;
import eu.ehri.project.persistance.ActionManager.EventContext;

/**
 * Class that serves as a manifest for an import batch,
 * detailing how many items were created and updated,
 * and how many failed.
 * 
 * @author mike
 *
 */
public class ImportLog {

	private int created = 0;
	private int updated = 0;
	private int errored = 0;
	private EventContext actionContet;
	private Map<String, String> errors = new HashMap<String, String>();
	
	
	/**
	 * Constructor.
	 * 
	 * @param action2
	 */
	public ImportLog(final EventContext action) {
		this.actionContet = action;
	}
	
	/**
	 * Increment the creation count.
	 */
	public void addCreated() {
		created++;
	}
		
	/**
	 * Increment the update count.
	 * 
	 */
	public void addUpdated() {
		updated++;
	}
	
	/**
	 * Get the number of created items.
	 * @return
	 */
	public int getCreated() {
		return created;
	}

	/**
	 * Get the number of updated items.
	 * 
	 * @return
	 */
	public int getUpdated() {
		return updated;
	}

	/**
	 * Get the number of errored item imports.
	 * 
	 * @return
	 */
	public int getErrored() {
		return errored;
	}

	/**
	 * Get the import errors.
	 * 
	 * @return
	 */
	public Map<String, String> getErrors() {
		return errors;
	}
	
	/**
	 * Indicate that importing the item with the given id
	 * failed with the given error.
	 * 
	 * @param item
	 * @param error
	 */
	public void setErrored(String item, String error) {
		errors.put(item, error);
		errored++;
	}
	
	/**
	 * Get the Action associated with this import.
	 * 
	 * @return
	 */
	public Action getAction() {
		return actionContet.getAction();
	}			
	
	/**
	 * Indicated whether the import succeeded at all,
	 * in terms of items created/updated.
	 * 
	 * @return
	 */
	public boolean isValid() {
		return created > 0 || updated > 0;
	}

	/**
	 * Get the number of items that were either created or updated.
	 * 
	 * @return
	 */
	public int getSuccessful() {
		return created + updated;
	}
}
