/**
 * Bridge infrastructure for routing MCP requests to backend services.
 * <p>
 * This package contains bridge implementations that mediate between MCP URIs
 * and the backend services capable of handling them. Bridges may support various
 * transport mechanisms and protocols for communicating with MCP servers, but for now
 * only grpc is supported/implemented.
 * <p>
 *
 * @see ai.wanaku.backend.bridge.Bridge
 */
package ai.wanaku.backend.bridge;
