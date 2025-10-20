import asyncio

from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.conditions import TextMentionTermination
from autogen_agentchat.teams import RoundRobinGroupChat
from autogen_agentchat.ui import Console
from autogen_ext.models.ollama import OllamaChatCompletionClient
from autogen_ext.models.openai import OpenAIChatCompletionClient
from autogen_ext.tools.mcp import SseServerParams, mcp_server_tools
from pydantic import BaseModel

import os

# Define output structure for the script
class ScriptOutput(BaseModel):
    topic: str
    takeaway: str
    captions: list[str]

async def main():

    # Get API key from environment variable WANAKU_TEST_API_KEY
    # Defaults to "placeholder" if WANAKU_TEST_API_KEY is not set
    api_key = os.getenv("WANAKU_TEST_API_KEY", "placeholder")

    # Get base URL from environment variable WANAKU_TEST_BASE_MODEL_URL
    # It will be None if WANAKU_TEST_BASE_MODEL_URL is not set, which might cause issues if not handled.
    # You might want to provide a default here as well if it's not strictly required to be set.
    base_url = os.getenv("WANAKU_TEST_BASE_MODEL_URL", "http://localhost:11434/v1") # Added a default for robustness

    # Same thing for the model
    model_name = os.getenv("WANAKU_TEST_MODEL", "llama3.1:latest")

    use_ollama_str = os.getenv("WANAKU_TEST_USE_OLLAMA", "false").lower()
    use_ollama = (use_ollama_str == "true")

    # Set base_url and model_name based on whether Ollama is used
    if use_ollama:
        model_client = OllamaChatCompletionClient(
            model=model_name,
            api_key=api_key,  # Placeholder API key for local model
            response_format=ScriptOutput,
            base_url=base_url,
            model_info={
                "function_calling": True,
                "json_output": True,
                "vision": False,
                "family": "unknown"
            }
        )
    else:
        model_client = OpenAIChatCompletionClient(
            model=model_name,
            base_url=base_url,
            api_key=api_key,
            model_info={
                "function_calling": True,
                "json_output": True,
                "vision": False,
                "family": "unknown"
            }
        )

    # Create server params for the remote MCP service
    server_params = SseServerParams(
        url="http://localhost:8080/mcp/sse"
    )

    # Get all available tools
    adapter = await mcp_server_tools(server_params)

    tester_assistant = AssistantAgent(
        name="tester_assistant",
        model_client=model_client,  # Swap with ollama_client if needed
        tools=adapter,
        system_message='''
            You are an assistant tasked with helping me with general questions. Your job consists of trying to answer the
            request to the best of your ability. You have tools at your disposal that you can call to answer them.
            Upon receiving the answer, try to extract the data without modifying the original content.
            If you don't know which tool to call, then simply reply 'There is no tool available for this request'.
        ''',
        reflect_on_tool_use=True,
    )


    # Set up termination condition
    termination = TextMentionTermination("TERMINATE")

    inputs_map = {
        "dog-facts": "Please give me 3 dog facts",
        "meow-facts": "Please give me 2 cat facts",
        # "tavily-search": "Please search on the web using Tavily 'What is Apache Spark?'. Then, summarize the results for me.",
    }

    participants = [tester_assistant]

    # Create sequential execution order
    # Use different agent groups with different max_rounds to ensure each agent completes its task
    agent_team = RoundRobinGroupChat(
        participants,
        termination_condition=termination,
        # Each agent gets one full turn
        max_turns=1
    )

    # for participant in participants:
    for tool in inputs_map.keys():
        prompt = inputs_map.get(tool, None)
        print("Using prompt: {}".format(prompt))
        stream = agent_team.run_stream(task=repr(prompt))
        await Console(stream)


# Run the main async function
if __name__ == "__main__":
    asyncio.run(main())
