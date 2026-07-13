package ai.wanaku.cli.main.commands.service;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "catalog",
        description = "Manage service catalogs",
        subcommands = {ServiceCatalogList.class, ServiceCatalogRemove.class})
public class ServiceCatalog extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
