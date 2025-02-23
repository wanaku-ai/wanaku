package org.wanaku.cli.main.support;

public class PropertyHelper {
    public record PropertyDescription(String propertyName, String dataType, String description) {
    }

    public static PropertyDescription parseProperty(String propertyStr) {
        int nameDelimiter = propertyStr.indexOf(":");
        int typeDelimiter = propertyStr.indexOf(",");
        String propertyName = propertyStr.substring(0, nameDelimiter);
        String dataType = propertyStr.substring(nameDelimiter + 1, typeDelimiter);
        String description = propertyStr.substring(typeDelimiter + 1);
        PropertyHelper.PropertyDescription result = new PropertyHelper.PropertyDescription(propertyName, dataType, description);
        return result;
    }
}
