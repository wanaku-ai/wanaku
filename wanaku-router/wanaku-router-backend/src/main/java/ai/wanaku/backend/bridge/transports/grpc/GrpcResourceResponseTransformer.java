package ai.wanaku.backend.bridge.transports.grpc;

import java.util.ArrayList;
import java.util.List;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import ai.wanaku.backend.bridge.ResourceResponseTransformer;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.exchange.v1.ResourceReply;
import com.google.protobuf.ProtocolStringList;

class GrpcResourceResponseTransformer implements ResourceResponseTransformer<ResourceReply> {

    /**
     * Processes the resource reply and converts it to resource contents.
     *
     * @param reply       the reply from the remote service
     * @param arguments   the original request arguments
     * @param mcpResource the resource reference
     * @return a list of resource contents
     */
    public List<ResourceContents> transformReply(
            ResourceReply reply, ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        ProtocolStringList contentList = reply.getContentList();
        List<ResourceContents> textResourceContentsList = new ArrayList<>();
        for (String content : contentList) {
            TextResourceContents textResourceContents =
                    new TextResourceContents(arguments.requestUri().value(), content, mcpResource.getMimeType());
            textResourceContentsList.add(textResourceContents);
        }
        return textResourceContentsList;
    }
}
