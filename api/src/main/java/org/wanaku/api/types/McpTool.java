package org.wanaku.api.types;

import java.util.List;

public class McpTool {
    public String name;
    public String description;
    public String uri;
    public String type;
    public InputSchema inputSchema;

    public static class InputSchema {
        public String type;
        public List<Property> properties;

        public static class Property {
            public String name;
            public String type;
            public String description;
            public boolean required;
        }
    }
}
