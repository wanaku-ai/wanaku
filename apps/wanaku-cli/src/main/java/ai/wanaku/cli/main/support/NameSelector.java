package ai.wanaku.cli.main.support;

import picocli.CommandLine;

public class NameSelector {
    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Select by name. Cannot be used with --label-expression.")
    public String name;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Select by label expression. Cannot be used with --name.")
    public String labelExpression;
}
