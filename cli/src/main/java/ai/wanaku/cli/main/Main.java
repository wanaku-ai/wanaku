package ai.wanaku.cli.main;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main {

    public static void main(String[] args) {
        Quarkus.run(CliMain.class, args);
    }
}
