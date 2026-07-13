package ai.wanaku.cli.main.support;

import picocli.CommandLine;

public class IdSelector {
    @CommandLine.Option(
            names = {"-i", "--id"},
            description = "Select by ID. Cannot be used with --label-expression.")
    public String id;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Select by label expression. Cannot be used with --id.")
    public String labelExpression;
}
