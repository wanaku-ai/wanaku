package ai.wanaku.core.services.api;

import java.util.List;

public record ServiceTemplateSummary(
        String id, String name, String icon, String description, List<String> services, boolean hasProperties) {}
