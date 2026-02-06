import React from "react";
import {
  Button,
  Column,
  DataTable,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tooltip,
} from "@carbon/react";
import { TrashCan, Download, View } from "@carbon/icons-react";
import type { DataStore } from "../../models";

interface DataStoresTableProps {
  dataStores: DataStore[];
  onDelete: (id: string) => void;
  onAdd: () => void;
  onDownload: (dataStore: DataStore) => void;
  onView: (dataStore: DataStore) => void;
}

export const DataStoresTable: React.FC<DataStoresTableProps> = ({
  dataStores,
  onDelete,
  onAdd,
  onDownload,
  onView,
}) => {
  const truncateData = (data?: string): string => {
    if (!data) return "";
    if (data.length <= 50) return data;
    return `${data.substring(0, 47)}...`;
  };

  return (
    <Grid>
      <Column lg={16} md={8} sm={4}>
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "1rem" }}>
          <Button onClick={onAdd}>Add Data Store</Button>
        </div>
        {dataStores.length === 0 ? (
          <div style={{ padding: "2rem", textAlign: "center", color: "#6f6f6f" }}>
            No data stores found. Click "Add Data Store" to upload a file.
          </div>
        ) : (
          <DataTable
            rows={dataStores.map((dataStore, index) => ({
              id: dataStore.id || `datastore-${index}`,
              name: dataStore.name || "N/A",
              data: dataStore.data || "",
              dataTruncated: truncateData(dataStore.data),
            }))}
            headers={[
              { key: "id", header: "ID" },
              { key: "name", header: "Name" },
              { key: "dataTruncated", header: "Data (Base64)" },
              { key: "actions", header: "Actions" },
            ]}
          >
            {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {headers.map((header) => (
                      <TableHeader {...getHeaderProps({ header })} key={header.key}>
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => {
                    const { key, ...rowProps } = getRowProps({ row });
                    const dataStore = dataStores.find((ds) => ds.id === row.id);

                    return (
                      <TableRow key={key} {...rowProps}>
                        {row.cells.map((cell) => {
                          if (cell.info.header === "dataTruncated") {
                            return (
                              <TableCell key={cell.id}>
                                <Tooltip label={dataStore?.data || ""} align="bottom">
                                  <span>{cell.value}</span>
                                </Tooltip>
                              </TableCell>
                            );
                          }
                          if (cell.info.header === "actions") {
                            return (
                              <TableCell key={cell.id}>
                                <Button
                                  kind="ghost"
                                  renderIcon={View}
                                  hasIconOnly
                                  iconDescription="View"
                                  onClick={() => dataStore && onView(dataStore)}
                                  size="sm"
                                  style={{ marginRight: "0.5rem" }}
                                />
                                <Button
                                  kind="ghost"
                                  renderIcon={Download}
                                  hasIconOnly
                                  iconDescription="Download"
                                  onClick={() => dataStore && onDownload(dataStore)}
                                  size="sm"
                                  style={{ marginRight: "0.5rem" }}
                                />
                                <Button
                                  kind="danger--ghost"
                                  renderIcon={TrashCan}
                                  hasIconOnly
                                  iconDescription="Delete"
                                  onClick={() => dataStore?.id && onDelete(dataStore.id)}
                                  size="sm"
                                />
                              </TableCell>
                            );
                          }
                          return <TableCell key={cell.id}>{cell.value}</TableCell>;
                        })}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
          </DataTable>
        )}
      </Column>
    </Grid>
  );
};
