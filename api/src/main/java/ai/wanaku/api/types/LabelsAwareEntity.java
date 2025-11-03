package ai.wanaku.api.types;

import java.util.HashMap;
import java.util.Map;

public abstract class LabelsAwareEntity<T> implements WanakuEntity<T> {

    private Map<String, String> labels;

    /**
     * Gets the labels as a map.
     *
     * @return the map of labels, never null
     */
    public Map<String, String> getLabels() {
        if (labels == null) {
            labels = new HashMap<>();
        }
        return labels;
    }

    /**
     * Sets the labels from a map.
     *
     * @param labels the map of labels
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    /**
     * Adds a single label.
     *
     * @param key the label key
     * @param value the label value
     */
    public void addLabel(String key, String value) {
        getLabels().put(key, value);
    }

    /**
     * Adds multiple labels from a map.
     *
     * @param labelsMap the labels to add
     */
    public void addLabels(Map<String, String> labelsMap) {
        if (labelsMap != null) {
            getLabels().putAll(labelsMap);
        }
    }

    /**
     * Gets the value of a specific label by key.
     *
     * @param labelKey the label key to look up
     * @return the label value, or null if not found
     */
    public String getLabelValue(String labelKey) {
        if (labels == null) {
            return null;
        }
        return labels.get(labelKey);
    }

    /**
     * Checks if a label with the given key exists.
     *
     * @param labelKey the label key to check
     * @return true if the label exists, false otherwise
     */
    public boolean hasLabel(String labelKey) {
        if (labels == null) {
            return false;
        }
        return labels.containsKey(labelKey);
    }

    /**
     * Checks if a label with the given key and value exists.
     *
     * @param labelKey the label key to check
     * @param labelValue the label value to check
     * @return true if the label exists with the given value, false otherwise
     */
    public boolean hasLabel(String labelKey, String labelValue) {
        if (labels == null) {
            return false;
        }
        return labelValue.equals(labels.get(labelKey));
    }

    /**
     * Removes a label by key.
     *
     * @param labelKey the label key to remove
     * @return true if a label was removed, false otherwise
     */
    public boolean removeLabel(String labelKey) {
        if (labels == null) {
            return false;
        }
        return labels.remove(labelKey) != null;
    }
}
