package ai.wanaku.api.types;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.List;

@Table(name = "RESOURCE_REFERENCE")
@Entity
public class ResourceReference {
    @Id
    private String location;
    private String type;
    private String name;
    private String description;
    private String mimeType;
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "location")
    private List<Param> params;

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public List<Param> getParams() {
        return params;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }

    @Table(name = "PARAM")
    @Entity
    public static class Param {
        @Id
        private String name;
        @Id
        private String location;
        private String paramValue;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return paramValue;
        }

        public void setValue(String value) {
            this.paramValue = value;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }
}
