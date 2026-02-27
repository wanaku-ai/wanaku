import mcp from 'k6/x/mcp';

export default function () {
    // Initialize MCP Client with stdio transport
    const client = new mcp.SSEClient({
        base_url: 'http://localhost:8080/public/mcp/sse',
        timeout: 5
    });

    // Check connection to MCP server
    console.log('MCP server running:', client.ping());

    // List all available resources
    console.log('Resources available:');
    const resources = client.listAllResources().resources;
    resources.forEach(resource => console.log(`  - ${resource.uri}`));

    // Read a sample resource
    const resourceContent = client.readResource({ uri: 'in-memory-file.txt' });
    console.log(`Resource content: ${resourceContent.contents[0].text}`);
}