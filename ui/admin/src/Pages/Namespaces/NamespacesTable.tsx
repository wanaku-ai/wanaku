import {Add, Edit, TrashCan} from "@carbon/icons-react";
import {
    Button,
    DataTable,
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
} from "@carbon/react";
import React from "react";
import {Namespace} from "../../models";

const PROTECTED_PATHS = ["default", "public", "wanaku-internal"];

interface NamespaceTableProps {
  namespaces: Namespace[];
  onAdd: () => void;
  onEdit: (namespace: Namespace) => void;
  onDelete: (namespace: Namespace) => void;
}

function isProtected(namespace: Namespace): boolean {
  return PROTECTED_PATHS.includes(namespace.name || "");
}

function hasLabels(namespace: Namespace): boolean {
  return !!namespace.labels && Object.keys(namespace.labels).length > 0;
}

function formatLabel(key: string, value: string): string {
  const shortKey = key.replace("wanaku.io/", "");
  if (shortKey.endsWith("-at") && /^\d+$/.test(value)) {
    const epoch = Number(value);
    const date = epoch > 1e12 ? new Date(epoch) : new Date(epoch * 1000);
    return `${shortKey}: ${date.toLocaleString()}`;
  }
  return `${shortKey}: ${value}`;
}

export const NamespaceTable: React.FC<NamespaceTableProps> = ({
  namespaces,
  onAdd,
  onEdit,
  onDelete,
}) => {
  const headers = [
    { key: "name", header: "Name" },
    { key: "status", header: "Status" },
    { key: "address", header: "Address" },
    { key: "actions", header: "Actions" },
  ];

  function mcpAddress(name?: string): string {
    const host = window.location.hostname;
    const protocol = window.location.protocol;
    const base = `${protocol}//${host}:8081`;
    if (!name || name === "default") return `${base}/mcp`;
    return `${base}/${name}/mcp`;
  }

  const rows = namespaces.map((namespace, index) => ({
    id: namespace.name || `namespace-${index}`,
    name: namespace.name || "N/A",
    status: namespace.name ? "Allocated" : "Available",
    address: mcpAddress(namespace.name),
    actions: "",
  }));

  return (
    <DataTable headers={headers} rows={rows}>
      {({
        headers,
        rows,
        getTableProps,
        getHeaderProps,
        getRowProps,
        getExpandedRowProps,
        getToolbarProps,
      }) => (
        <TableContainer>
          <TableToolbar {...getToolbarProps()}>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd}>
                Create Namespace
              </Button>
            </TableToolbarContent>
          </TableToolbar>
          <Table {...getTableProps()} aria-label="Namespaces table">
            <TableHead>
              <TableRow>
                <TableExpandHeader />
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({ header })} key={header.key}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                const namespace = namespaces.find((ns) => ns.name === row.id);
                if (!namespace) return null;
                const expandable = hasLabels(namespace);
                const nsProtected = isProtected(namespace);

                const cells = (
                  <React.Fragment>
                    <TableCell>{namespace.name || "N/A"}</TableCell>
                    <TableCell>{namespace.name ? "Allocated" : "Available"}</TableCell>
                    <TableCell>{mcpAddress(namespace.name)}</TableCell>
                    <TableCell>
                      <Button
                        kind="ghost"
                        renderIcon={Edit}
                        hasIconOnly
                        iconDescription="Edit"
                        disabled={nsProtected}
                        onClick={() => onEdit(namespace)}
                      />
                      <Button
                        kind="ghost"
                        renderIcon={TrashCan}
                        hasIconOnly
                        iconDescription="Delete"
                        disabled={nsProtected}
                        onClick={() => onDelete(namespace)}
                      />
                    </TableCell>
                  </React.Fragment>
                );

                if (expandable) {
                  return (
                    <React.Fragment key={namespace.name}>
                      <TableExpandRow expandIconDescription="Show labels" {...getRowProps({ row })}>
                        {cells}
                      </TableExpandRow>
                      {row.isExpanded && (
                        <TableExpandedRow colSpan={headers.length + 3} {...getExpandedRowProps({ row })}>
                          <div>
                            <strong>Labels:</strong>
                            <ul style={{ margin: "0.5rem 0", paddingLeft: "1.5rem" }}>
                              {Object.entries(namespace.labels || {}).map(([key, value]) => (
                                <li key={key}>{formatLabel(key, value)}</li>
                              ))}
                            </ul>
                          </div>
                        </TableExpandedRow>
                      )}
                    </React.Fragment>
                  );
                }

                return (
                  <TableRow {...getRowProps({ row })} key={namespace.name}>
                    <TableCell />
                    {cells}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
