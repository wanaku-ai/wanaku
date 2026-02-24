import React, {useState} from "react";
import {
    Button,
    DataTable,
    Modal,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableExpandedRow,
    TableExpandHeader,
    TableExpandRow,
    TableHead,
    TableHeader,
    TableRow,
    TableToolbar,
    TableToolbarContent,
    TableToolbarSearch,
} from "@carbon/react";
import {TrashCan} from "@carbon/icons-react";

interface ServiceCatalogSystem {
  name: string;
  routesFile: string;
  rulesFile: string;
  dependenciesFile?: string;
}

interface ServiceCatalogSummary {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: string[];
}

interface ServiceCatalogDetail {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: ServiceCatalogSystem[];
}

interface ServiceCatalogTableProps {
  catalogs: ServiceCatalogSummary[];
  onDelete: (name: string) => void;
  onSearch: (search: string) => void;
  getDetail: (name: string) => Promise<ServiceCatalogDetail | null>;
}

const headers = [
  { key: "name", header: "Name" },
  { key: "icon", header: "Icon" },
  { key: "description", header: "Description" },
  { key: "systemCount", header: "Systems" },
  { key: "actions", header: "Actions" },
];

export const ServiceCatalogTable: React.FC<ServiceCatalogTableProps> = ({
  catalogs,
  onDelete,
  onSearch,
  getDetail,
}) => {
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [expandedDetails, setExpandedDetails] = useState<Record<string, ServiceCatalogDetail>>({});

  const handleExpandRow = async (name: string) => {
    if (expandedDetails[name]) return;
    const detail = await getDetail(name);
    if (detail) {
      setExpandedDetails((prev) => ({ ...prev, [name]: detail }));
    }
  };

  return (
    <>
      <DataTable
        rows={catalogs.map((catalog, index) => ({
          id: catalog.id || `catalog-${index}`,
          name: catalog.name || "N/A",
          icon: catalog.icon || "",
          description: catalog.description || "",
          systemCount: catalog.services?.length ?? 0,
        }))}
        headers={headers}
      >
        {({ rows, headers, getTableProps, getHeaderProps, getRowProps, getExpandHeaderProps }) => (
          <TableContainer>
            <TableToolbar>
              <TableToolbarContent>
                <TableToolbarSearch
                  onChange={(_event: "" | React.ChangeEvent<HTMLInputElement>, value?: string) => onSearch(value ?? "")}
                  placeholder="Search service catalogs..."
                />
              </TableToolbarContent>
            </TableToolbar>
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  <TableExpandHeader {...getExpandHeaderProps()} />
                  {headers.map((header) => (
                    <TableHeader {...getHeaderProps({ header })} key={header.key}>
                      {header.header}
                    </TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {catalogs.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={headers.length + 1} style={{ textAlign: "center", color: "var(--cds-text-secondary)" }}>
                      No service catalogs found. Use the CLI to deploy a service catalog:
                      <code style={{ display: "block", marginTop: "0.5rem" }}>wanaku service init --name=myservice --services=system1</code>
                    </TableCell>
                  </TableRow>
                ) : (
                  rows.map((row) => {
                    const catalog = catalogs.find((c) => c.id === row.id);
                    const catalogName = catalog?.name || "";
                    const detail = expandedDetails[catalogName];

                    return (
                      <React.Fragment key={row.id}>
                        <TableExpandRow
                          {...getRowProps({ row })}
                          onExpand={() => handleExpandRow(catalogName)}
                        >
                          {row.cells.map((cell) => {
                            if (cell.info.header === "actions") {
                              return (
                                <TableCell key={cell.id}>
                                  <Button
                                    kind="ghost"
                                    renderIcon={TrashCan}
                                    hasIconOnly
                                    iconDescription="Delete"
                                    onClick={() => setDeleteTarget(catalogName)}
                                  />
                                </TableCell>
                              );
                            }
                            return <TableCell key={cell.id}>{cell.value}</TableCell>;
                          })}
                        </TableExpandRow>
                        <TableExpandedRow colSpan={headers.length + 1}>
                          {detail ? (
                            <div style={{ padding: "1rem" }}>
                              <h5>Systems</h5>
                              {detail.services.map((system) => (
                                <div key={system.name} style={{ marginBottom: "0.75rem", paddingLeft: "1rem" }}>
                                  <strong>{system.name}</strong>
                                  <ul style={{ listStyle: "none", padding: 0, margin: "0.25rem 0 0 0" }}>
                                    <li>Routes: <code>{system.routesFile}</code></li>
                                    <li>Rules: <code>{system.rulesFile}</code></li>
                                    {system.dependenciesFile && (
                                      <li>Dependencies: <code>{system.dependenciesFile}</code></li>
                                    )}
                                  </ul>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div style={{ padding: "1rem" }}>Loading system details...</div>
                          )}
                        </TableExpandedRow>
                      </React.Fragment>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DataTable>

      {deleteTarget && (
        <Modal
          open={true}
          modalHeading="Delete Service Catalog"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          danger
          onRequestClose={() => setDeleteTarget(null)}
          onRequestSubmit={() => {
            onDelete(deleteTarget);
            setDeleteTarget(null);
          }}
        >
          <p>Are you sure you want to delete the service catalog <strong>{deleteTarget}</strong>? This action cannot be undone.</p>
        </Modal>
      )}
    </>
  );
};
