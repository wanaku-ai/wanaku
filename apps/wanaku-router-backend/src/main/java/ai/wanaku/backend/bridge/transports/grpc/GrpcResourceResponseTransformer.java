package ai.wanaku.backend.bridge.transports.grpc;

import java.util.ArrayList;
import java.util.List;
import io.modelcontextprotocol.spec.McpSchema;
import ai.wanaku.backend.bridge.ResourceResponseTransformer;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.exchange.v1.ResourceReply;
import com.google.protobuf.ProtocolStringList;

class GrpcResourceResponseTransformer implements ResourceResponseTransformer<ResourceReply> {

    public List<McpSchema.ResourceContents> transformReply(
            ResourceReply reply, McpSchema.ReadResourceRequest readRequest, ResourceReference mcpResource) {
        ProtocolStringList contentList = reply.getContentList();
        List<McpSchema.ResourceContents> resourceContentsList = new ArrayList<>();
        for (String content : contentList) {
            McpSchema.TextResourceContents textResourceContents =
                    new McpSchema.TextResourceContents(readRequest.uri(), mcpResource.getMimeType(), content);
            resourceContentsList.add(textResourceContents);
        }
        return resourceContentsList;
    }
}
