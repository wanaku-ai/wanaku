package ai.wanaku.api.types.management;

import java.util.Map;

public class Service {
    private String target;
    private Configurations configurations;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Configurations getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Configurations configurations) {
        this.configurations = configurations;
    }


    public static Service newService(String target, Map<String, String> toolsConfigurations) {
        Service srv = new Service();
        srv.setTarget(target);
        Configurations configurations = new Configurations();

        for (Map.Entry<String, String> entry : toolsConfigurations.entrySet()) {
            Configuration cfg = new Configuration();
            cfg.setDescription(entry.getValue());
            configurations.getConfigurations().put(entry.getKey(), cfg);
        }
        srv.setConfigurations(configurations);
        return srv;
    }
}
