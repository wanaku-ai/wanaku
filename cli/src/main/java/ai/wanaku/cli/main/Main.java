package ai.wanaku.cli.main;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Main entry point for the Wanaku CLI application.
 * <p>
 * This class bootstraps the Quarkus-based command-line interface for managing
 * the Wanaku router and its capabilities. The CLI provides commands for:
 * <ul>
 *   <li>Managing tool and resource capabilities</li>
 *   <li>Configuring namespaces and forwards</li>
 *   <li>Monitoring service health and activity</li>
 *   <li>Interacting with the Wanaku router API</li>
 * </ul>
 * </p>
 * <p>
 * The actual CLI command implementation is delegated to {@link CliMain},
 * which is executed within the Quarkus runtime environment.
 * </p>
 *
 * @see CliMain
 */
@QuarkusMain
public class Main {

    /**
     * Application entry point.
     * <p>
     * Initializes the Quarkus runtime and executes the CLI application.
     * </p>
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        Quarkus.run(CliMain.class, args);
    }
}
