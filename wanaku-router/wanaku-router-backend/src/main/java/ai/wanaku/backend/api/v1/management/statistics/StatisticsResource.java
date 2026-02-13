package ai.wanaku.backend.api.v1.management.statistics;

import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/api/v1/management/statistics")
public class StatisticsResource {

    @Inject
    StatisticsBean statisticsBean;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<SystemStatistics> getStatistics() {
        return new WanakuResponse<>(statisticsBean.getStatistics());
    }
}
