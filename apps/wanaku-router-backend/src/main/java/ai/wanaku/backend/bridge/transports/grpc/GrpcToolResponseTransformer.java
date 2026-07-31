package ai.wanaku.backend.bridge.transports.grpc;

import java.util.ArrayList;
import java.util.List;
import io.modelcontextprotocol.spec.McpSchema;
import ai.wanaku.backend.bridge.ToolResponseTransformer;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import com.google.protobuf.ProtocolStringList;

class GrpcToolResponseTransformer implements ToolResponseTransformer<ToolInvokeReply> {

    public McpSchema.CallToolResult transformReply(ToolInvokeReply reply) {
        ProtocolStringList contentList = reply.getContentList();
        List<McpSchema.Content> contents = new ArrayList<>(contentList.size());
        contentList.stream()
                .map(text ->
                        (McpSchema.Content) McpSchema.TextContent.builder(text).build())
                .forEach(contents::add);

        return McpSchema.CallToolResult.builder(contents).build();
    }
}
