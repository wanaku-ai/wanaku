package ai.wanaku.core.services.routing;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.exchange.ToolInvokeRequest;

public interface Client {
    Object exchange(ToolInvokeRequest request) throws WanakuException;
}
