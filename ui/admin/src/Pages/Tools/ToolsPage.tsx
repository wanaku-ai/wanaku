import {InlineNotification} from "@carbon/react";
import {PageSkeleton} from "../../components/PageSkeleton";
import React, {useCallback, useEffect, useState} from "react";
import {useTools} from "../../hooks/api/use-tools";
import {ToolEntry} from "../../models";
import {ToolsTable} from "./ToolsTable";
import {ToolModal} from "./ToolModal"
import {unwrapData} from "../../utils/api-response";


export const ToolsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ToolEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [openedTool, setOpenedTool] = useState<ToolEntry>()
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listTools, updateTool, removeTool } = useTools();

  const updateTools = useCallback(async () => {
    return listTools().then((result: any) => {
      const data = unwrapData<ToolEntry[]>(result);
      if (result.status !== 200 || !Array.isArray(data)) {
        setErrorMessage("Failed to fetch tools. Please try again later.");
        setFetchedData([]);
      } else {
        setFetchedData(data);
      }

      setIsLoading(false);
    });
  }, [listTools]);

  useEffect(() => {
    updateTools();
  }, [updateTools]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10_000);

      return () => {
        clearTimeout(timer);
      };
    }
  }, [errorMessage]);

  if (isLoading) return <PageSkeleton title="Tools" />;

  function handleToolModalClose(): void {
    setOpenedTool(undefined)
    setIsEditModalOpen(false)
  }

  const handleUpdateTool = async(tool: ToolEntry) => {
    try {
      await updateTool(openedTool!.name!, tool)
      setErrorMessage(null)
      await updateTools();
    } catch (error) {
      setErrorMessage(`Error updating tool: ${error instanceof Error ? error.message : tool.name}`)
    } finally {
      handleToolModalClose()
    }
  }

  const handleDeleteTool = async (toolName?: string) => {
    try {
      if (!toolName) return;
      await removeTool(toolName);
      await updateTools();
    } catch {
      setErrorMessage(`Failed to delete tool: ${toolName}`);
    }
  };

  return (
    <div>
      {errorMessage && (
        <InlineNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          lowContrast
          hideCloseButton={false}
        />
      )}
      <h1 className="title">Tools</h1>
      <p className="description">
        A tool enables LLMs to execute tasks beyond their inherent capabilities
        by utilizing these tools. Each tool is uniquely identified by a name and
        defined with an input schema outlining the expected parameters.
        Tools are auto-discovered from forwarded MCP servers. Configure forwarded MCP servers from the Forwards page.
      </p>
      <div id="page-content">
        {fetchedData && (
          <ToolsTable
            fetchedData={fetchedData}
            onDelete={handleDeleteTool}
            onEdit={(tool: ToolEntry) => { setOpenedTool(tool); setIsEditModalOpen(true) }}
          />
        )}
        {isEditModalOpen && openedTool && (
          <ToolModal
            tools={fetchedData}
            tool={openedTool}
            onRequestClose={handleToolModalClose}
            onSubmit={handleUpdateTool}
            onError={(msg) => setErrorMessage(msg)}
          />
        )}
      </div>
    </div>
  );
};
