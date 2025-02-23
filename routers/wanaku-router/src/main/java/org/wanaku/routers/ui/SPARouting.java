package org.wanaku.routers.ui;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SPARouting {

    public void init(@Observes Router router) {
        router.get("/*").last().handler(rc -> {
            rc.reroute("/");
        });
    }
}
