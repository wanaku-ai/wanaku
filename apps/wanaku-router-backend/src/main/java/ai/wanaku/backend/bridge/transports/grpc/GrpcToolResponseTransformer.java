package ai.wanaku.backend.bridge.transports.grpc;

import java.util.ArrayList;
import java.util.List;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;
import ai.wanaku.backend.bridge.ToolResponseTransformer;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import com.google.protobuf.ProtocolStringList;

class GrpcToolResponseTransformer implements ToolResponseTransformer<ToolInvokeReply> {

    /**
     * Processes the tool invoke reply and converts it to a tool response.
     *
     * @param reply the reply from the remote tool invocation
     * @return a ToolResponse containing the execution results
     */
    public ToolResponse transformReply(ToolInvokeReply reply) {
        ProtocolStringList contentList = reply.getContentList();
        List<TextContent> contents = new ArrayList<>(contentList.size());
        contentList.stream().map(TextContent::new).forEach(contents::add);

        return ToolResponse.success(contents);
    }
}
