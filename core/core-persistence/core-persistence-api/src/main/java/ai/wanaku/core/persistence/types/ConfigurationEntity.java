package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.management.Configuration;

public class ConfigurationEntity {

    private String key;
    private Configuration configuration;

    public ConfigurationEntity() {}

    public ConfigurationEntity(String key, Configuration configuration) {
        this.key = key;
        this.configuration = configuration;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
