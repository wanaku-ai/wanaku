package ai.wanaku.api.types;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CONFIGURATION")
public class Configuration {
    @Id
    private String service;
    @Id
    private String option;
    private String value;
    private ServiceType type;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
