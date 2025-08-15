package ai.wanaku.jbang;

// JAVA 17+
// REPOS mavencentral
// Without following config, the @Inject doesn't know about eg. WanakuCliConfig
// Q:CONFIG quarkus.index-dependency.wanaku.group-id=ai.wanaku
// Q:CONFIG quarkus.index-dependency.wanaku.artifact-id=cli

import ai.wanaku.cli.main.Main;

// Extends just to ensure, that deletion of ai.wanaku.cli.main.Main will also break JBang script
public class WanakuJBang extends Main {}
