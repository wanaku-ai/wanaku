import React, {useCallback, useEffect, useState} from "react";
import {
  Button,
  Column,
  DataTable,
  Grid,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableSelectAll,
  TableSelectRow,
  TextInput,
  Tile,
} from "@carbon/react";
import {Add, ChevronDown, ChevronUp, TrashCan} from "@carbon/icons-react";
import {
  ToolsetEntry,
  ToolsetRepoCatalog,
  ToolsetRepoSummary,
  useToolsetRepos,
} from "../../hooks/api/use-toolset-repos";
import {ToolReference} from "../../models";
import {postApiV1Tools} from "../../api/wanaku-router-api";

interface ToolsetReposTabProps {
  onError: (msg: string) => void;
  onSuccess: (msg: string) => void;
}

export const ToolsetReposTab: React.FC<ToolsetReposTabProps> = ({onError, onSuccess}) => {
  const [repos, setRepos] = useState<ToolsetRepoSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [browseTarget, setBrowseTarget] = useState<string | null>(null);
  const [browseCatalog, setBrowseCatalog] = useState<ToolsetRepoCatalog | null>(null);
  const [importToolsetName, setImportToolsetName] = useState<string | null>(null);
  const [importTools, setImportTools] = useState<ToolReference[]>([]);
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([]);
  const [isBrowsing, setIsBrowsing] = useState(false);

  const [addName, setAddName] = useState("");
  const [addUrl, setAddUrl] = useState("");
  const [addDescription, setAddDescription] = useState("");

  const {listRepos, addRepo, removeRepo, browseRepo, fetchToolset} = useToolsetRepos();

  const loadRepos = useCallback(async () => {
    try {
      const result = await listRepos();
      setRepos(result.data.data || []);
      setIsLoading(false);
    } catch (error) {
      console.error("Error fetching toolset repos:", error);
      onError("Failed to load toolset repositories");
      setIsLoading(false);
    }
  }, [listRepos, onError]);

  useEffect(() => {
    loadRepos();
  }, [loadRepos]);

  const handleAdd = async () => {
    try {
      await addRepo({name: addName, url: addUrl, description: addDescription || undefined});
      onSuccess(`Toolset repository '${addName}' added`);
      setShowAddModal(false);
      setAddName("");
      setAddUrl("");
      setAddDescription("");
      loadRepos();
    } catch (error) {
      console.error("Error adding repo:", error);
      onError("Failed to add toolset repository");
    }
  };

  const handleDelete = async (name: string) => {
    try {
      await removeRepo(name);
      onSuccess(`Toolset repository '${name}' removed`);
      setDeleteTarget(null);
      loadRepos();
    } catch (error) {
      console.error("Error removing repo:", error);
      onError(`Failed to remove repository '${name}'`);
    }
  };

  const handleBrowse = async (name: string) => {
    setIsBrowsing(true);
    setBrowseTarget(name);
    try {
      const result = await browseRepo(name);
      setBrowseCatalog(result.data.data);
    } catch (error) {
      console.error("Error browsing repo:", error);
      onError(`Failed to browse repository '${name}'`);
      setBrowseTarget(null);
    } finally {
      setIsBrowsing(false);
    }
  };

  const handleFetchToolset = async (repoName: string, toolsetName: string) => {
    try {
      const result = await fetchToolset(repoName, toolsetName);
      const tools = result.data.data || [];
      setImportToolsetName(toolsetName);
      setImportTools(tools);
      setSelectedToolIds(tools.filter((t: ToolReference) => t.name).map((t: ToolReference) => t.name!));
    } catch (error) {
      console.error("Error fetching toolset:", error);
      onError(`Failed to fetch toolset '${toolsetName}'`);
    }
  };

  const handleImportSelected = async () => {
    const toImport = importTools.filter((t) => t.name && selectedToolIds.includes(t.name));
    let imported = 0;
    for (const tool of toImport) {
      try {
        await postApiV1Tools(tool);
        imported++;
      } catch (error) {
        console.error(`Error importing tool '${tool.name}':`, error);
      }
    }
    onSuccess(`Imported ${imported} tool(s) from '${importToolsetName}'`);
    setImportToolsetName(null);
    setImportTools([]);
    setSelectedToolIds([]);
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  const toolHeaders = [
    {key: "name", header: "Name"},
    {key: "description", header: "Description"},
    {key: "type", header: "Type"},
    {key: "uri", header: "URI"},
  ];

  const toolRows = importTools.map((t, i) => ({
    id: t.name || `tool-${i}`,
    name: t.name || "",
    description: t.description || "",
    type: t.type || "",
    uri: t.uri || "",
  }));

  return (
    <>
      <div style={{display: "flex", justifyContent: "flex-end", marginBottom: "1rem"}}>
        <Button renderIcon={Add} size="sm" onClick={() => setShowAddModal(true)}>
          Add Repository
        </Button>
      </div>

      {repos.length === 0 ? (
        <Tile className="catalog-empty-tile">
          <p>No toolset repositories registered.</p>
          <p className="catalog-empty-hint">
            Add a remote toolset repository to browse and import tools.
          </p>
        </Tile>
      ) : (
        <Grid className="catalog-grid">
          {repos.map((repo, index) => (
            <Column lg={4} md={4} sm={4} key={repo.name || `repo-${index}`}>
              <Tile className="catalog-card">
                <div className="catalog-card-header">
                  <div className="catalog-card-title-row">
                    {repo.icon && <span className="catalog-card-icon">{repo.icon}</span>}
                    <h4 className="catalog-card-name">{repo.name}</h4>
                  </div>
                  <Button
                    kind="ghost"
                    size="sm"
                    renderIcon={TrashCan}
                    hasIconOnly
                    iconDescription="Delete"
                    className="catalog-card-delete"
                    onClick={(e: React.MouseEvent) => {
                      e.stopPropagation();
                      setDeleteTarget(repo.name);
                    }}
                  />
                </div>

                <p className="catalog-card-description">{repo.description || repo.url}</p>

                <Button
                  kind="ghost"
                  size="sm"
                  renderIcon={browseTarget === repo.name ? ChevronUp : ChevronDown}
                  className="catalog-card-expand"
                  onClick={() => {
                    if (browseTarget === repo.name) {
                      setBrowseTarget(null);
                      setBrowseCatalog(null);
                    } else {
                      handleBrowse(repo.name);
                    }
                  }}
                >
                  {browseTarget === repo.name ? "Hide toolsets" : "Browse toolsets"}
                </Button>

                {browseTarget === repo.name && (
                  <div className="catalog-card-details">
                    {isBrowsing ? (
                      <p className="catalog-loading">Loading toolsets...</p>
                    ) : browseCatalog ? (
                      browseCatalog.toolsets.map((toolset: ToolsetEntry) => (
                        <div key={toolset.name} className="catalog-system" style={{marginBottom: "0.5rem"}}>
                          <div style={{display: "flex", justifyContent: "space-between", alignItems: "center"}}>
                            <div>
                              {toolset.icon && <span style={{marginRight: "0.25rem"}}>{toolset.icon}</span>}
                              <strong className="catalog-system-name">{toolset.name}</strong>
                              {toolset.description && (
                                <span style={{marginLeft: "0.5rem", fontSize: "0.8125rem", color: "var(--cds-text-secondary, #525252)"}}>
                                  {toolset.description}
                                </span>
                              )}
                            </div>
                            <Button
                              kind="ghost"
                              size="sm"
                              onClick={() => handleFetchToolset(repo.name, toolset.name)}
                            >
                              Import
                            </Button>
                          </div>
                        </div>
                      ))
                    ) : null}
                  </div>
                )}
              </Tile>
            </Column>
          ))}
        </Grid>
      )}

      {showAddModal && (
        <Modal
          open={true}
          modalHeading="Add Toolset Repository"
          primaryButtonText="Add"
          primaryButtonDisabled={!addName || !addUrl}
          secondaryButtonText="Cancel"
          onRequestSubmit={handleAdd}
          onRequestClose={() => setShowAddModal(false)}
        >
          <TextInput
            id="repo-name"
            labelText="Name"
            placeholder="e.g. wanaku-toolsets"
            required
            value={addName}
            onChange={(e) => setAddName(e.target.value)}
            style={{marginBottom: "1rem"}}
          />
          <TextInput
            id="repo-url"
            labelText="Base URL"
            placeholder="e.g. https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/main"
            required
            value={addUrl}
            onChange={(e) => setAddUrl(e.target.value)}
            style={{marginBottom: "1rem"}}
          />
          <TextInput
            id="repo-description"
            labelText="Description (optional)"
            placeholder="Short description of the repository"
            value={addDescription}
            onChange={(e) => setAddDescription(e.target.value)}
          />
        </Modal>
      )}

      {deleteTarget && (
        <Modal
          open={true}
          modalHeading="Delete Toolset Repository"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          danger
          onRequestClose={() => setDeleteTarget(null)}
          onRequestSubmit={() => handleDelete(deleteTarget)}
        >
          <p>
            Are you sure you want to remove the repository{" "}
            <strong>{deleteTarget}</strong>? This will not remove any previously imported tools.
          </p>
        </Modal>
      )}

      {importToolsetName && importTools.length > 0 && (
        <Modal
          open={true}
          modalHeading={`Import tools from "${importToolsetName}"`}
          primaryButtonText={`Import ${selectedToolIds.length} tool(s)`}
          primaryButtonDisabled={selectedToolIds.length === 0}
          secondaryButtonText="Cancel"
          size="lg"
          onRequestSubmit={handleImportSelected}
          onRequestClose={() => {
            setImportToolsetName(null);
            setImportTools([]);
            setSelectedToolIds([]);
          }}
        >
          <DataTable rows={toolRows} headers={toolHeaders}>
            {({rows, headers, getHeaderProps, getRowProps, getSelectionProps, getTableProps}) => (
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    <TableSelectAll
                      {...getSelectionProps()}
                      checked={selectedToolIds.length === importTools.length}
                      indeterminate={selectedToolIds.length > 0 && selectedToolIds.length < importTools.length}
                      onSelect={() => {
                        if (selectedToolIds.length === importTools.length) {
                          setSelectedToolIds([]);
                        } else {
                          setSelectedToolIds(importTools.filter((t) => t.name).map((t) => t.name!));
                        }
                      }}
                    />
                    {headers.map((header) => (
                      <TableHeader {...getHeaderProps({header})} key={header.key}>
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow {...getRowProps({row})} key={row.id}>
                      <TableSelectRow
                        {...getSelectionProps({row})}
                        checked={selectedToolIds.includes(row.id)}
                        onSelect={() => {
                          setSelectedToolIds((prev) =>
                            prev.includes(row.id) ? prev.filter((id) => id !== row.id) : [...prev, row.id]
                          );
                        }}
                      />
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>{cell.value}</TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </DataTable>
        </Modal>
      )}
    </>
  );
};
