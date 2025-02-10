package org.wanaku.cli.main.support;

import java.util.List;

import org.wanaku.api.types.ToolReference;

public class PrettyPrinter {

    public static void printParseableTools(final ToolReference toolReference) {
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
            printParseableTools(toolReference);
        }
    }

}
