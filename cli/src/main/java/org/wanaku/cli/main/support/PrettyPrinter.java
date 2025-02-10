package org.wanaku.cli.main.support;

import java.util.List;

import org.wanaku.api.types.ResourceReference;
import org.wanaku.api.types.ToolReference;

public class PrettyPrinter {

    public static void printParseable(final ToolReference toolReference) {
        System.out.printf("%-15s => %-15s => %-30s    %n",
                toolReference.getName(), toolReference.getType(), toolReference.getUri());
    }

    /**
     * Prints a list of packages
     * @param list the list of packages
     */
    public static void printTools(final List<ToolReference> list) {
        System.out.printf("%-15s    %-15s    %-30s    %n",
                "Name", "Type", "URI");

        for (ToolReference toolReference : list) {
            printParseable(toolReference);
        }
    }

    public static void printParseable(final ResourceReference resourceReference) {
        System.out.printf("%-20s => %-15s => %-30s    %s%n",
                resourceReference.getName(), resourceReference.getType(), resourceReference.getLocation(),
                resourceReference.getDescription());
    }

    /**
     * Prints a list of packages
     * @param list the list of packages
     */
    public static void printResources(final List<ResourceReference> list) {
        System.out.printf("%-20s    %-15s    %-30s    %s%n",
                "Name", "Type", "Location", "Description");

        for (ResourceReference packageInfo : list) {
            printParseable(packageInfo);
        }
    }

}
