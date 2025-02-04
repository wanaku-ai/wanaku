package org.wanaku.server.quarkus;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain(name = "base")
public class ServerMain {

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
