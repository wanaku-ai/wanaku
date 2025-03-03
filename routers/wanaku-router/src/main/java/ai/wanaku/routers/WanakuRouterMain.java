/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.routers;

import jakarta.inject.Inject;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

/**
 * Main class for the router
 */
@QuarkusMain(name = "base")
@CommandLine.Command(name = "camel", mixinStandardHelpOptions = true)
public class WanakuRouterMain implements Runnable, QuarkusApplication {
    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Option(names = {"--indexes-path"}, description = "The path to the index directory",
            defaultValue = "${user.home}/.wanaku/router/")
    private String indexesPath;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    public static void main(String[] args) {
        Quarkus.run(WanakuRouterMain.class, args);
    }

    @Override
    public int run(String... args) {
        return new CommandLine(this, factory).execute(args);
    }

    @Override
    public void run() {
        Quarkus.waitForExit();
    }
}
