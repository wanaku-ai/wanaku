package ai.wanaku.cli.main.commands.service;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "template",
        description = "Manage service templates",
        subcommands = {ServiceTemplateList.class, ServiceTemplateInstantiate.class})
public class ServiceTemplate extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
