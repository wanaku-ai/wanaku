package ai.wanaku.backend.api.v1.management.info;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.management.ServerInfo;
import ai.wanaku.core.util.VersionHelper;

@ApplicationScoped
@Path("/api/v1/management/info")
public class InfoResource {

    @Path("/version")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ServerInfo> version() {
        ServerInfo si = new ServerInfo();
        si.setVersion(VersionHelper.VERSION);

        return new WanakuResponse<>(si);
    }
}
