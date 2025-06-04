package ai.wanaku.core.util.support;

import java.util.ArrayList;
import java.util.List;

import ai.wanaku.api.types.ResourceReference;

public class ResourcesHelper {
    public static ResourceReference createResource(String location, String type, String name) {
        ResourceReference resource = new ResourceReference();

        // Set mock data using getters and setters
        resource.setLocation(location);
        resource.setType(type);
        resource.setName(name);
        resource.setDescription("A sample image resource");

        // Create a list of Param objects for the resource's params
        List<ResourceReference.Param> params = new ArrayList<>();

        // Add some example param data to the list
        ResourceReference.Param param1 = new ResourceReference.Param();
        param1.setName("param1");
        param1.setValue("value1");
        params.add(param1);

        ResourceReference.Param param2 = new ResourceReference.Param();
        param2.setName("param2");
        param2.setValue("value2");
        params.add(param2);

        // Set the list of params for the resource
        resource.setParams(params);

        return resource;
    }
}
