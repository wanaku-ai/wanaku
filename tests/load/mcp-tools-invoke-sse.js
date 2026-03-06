import mcp from 'k6/x/mcp';

export default function () {
    // Initialize MCP Client with stdio transport
    const client = new mcp.SSEClient({
        base_url: 'http://localhost:8080/public/mcp/sse',
        timeout: 5
    });

    // Check connection to MCP server
    console.log('MCP server running:', client.ping());

    // List all available tools
    console.log('Tools available:');
    const tools = client.listAllTools().tools;
    tools.forEach(tool => console.log(`  - ${tool.name}`));

    // Call a sample tool
    const toolResult = client.callTool({ name: 'performancenoop', arguments: { name: 'Sample arg' } });
    console.log(`Greet tool response: "${toolResult.content[0].text}"`);
}