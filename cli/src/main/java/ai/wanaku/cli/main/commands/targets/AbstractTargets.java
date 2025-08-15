package ai.wanaku.cli.main.commands.targets;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;
import org.jline.terminal.Terminal;

@Deprecated
@Command(name = "state", description = "Get the state of the targeted services")
public abstract class AbstractTargets extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    protected CapabilitiesService capabilitiesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        printer.printWarningMessage("`wanaku targets` is deprecated, use `wanaku capabilities` instead");
        capabilitiesService = initService(CapabilitiesService.class, host);
        return doTargetCall(printer);
    }

    protected abstract Integer doTargetCall(WanakuPrinter printer) throws Exception;
}
