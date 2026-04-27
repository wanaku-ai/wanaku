package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuServiceCatalogSpec {
    private String routerRef;
    private List<CatalogEntrySpec> catalogs;

    public String getRouterRef() {
        return routerRef;
    }

    public void setRouterRef(String routerRef) {
        this.routerRef = routerRef;
    }

    public List<CatalogEntrySpec> getCatalogs() {
        return catalogs;
    }

    public void setCatalogs(List<CatalogEntrySpec> catalogs) {
        this.catalogs = catalogs;
    }

    public static class CatalogEntrySpec {
        private String name;
        private String configMapRef;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getConfigMapRef() {
            return configMapRef;
        }

        public void setConfigMapRef(String configMapRef) {
            this.configMapRef = configMapRef;
        }
    }
}
