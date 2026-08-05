import React from "react";
import {
    Button,
    DataTable,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableHeader,
    TableRow,
    TableToolbar,
    TableToolbarContent,
    Tooltip,
} from "@carbon/react";
import {Add, Download, TrashCan, View} from "@carbon/icons-react";
import type {DataStore} from "../../models";

interface DataStoresTableProps {
  dataStores: DataStore[];
  onDelete: (id: string) => void;
  onAdd: () => void;
  onDownload: (dataStore: DataStore) => void;
  onView: (dataStore: DataStore) => void;
}

const headers = [
  { key: "id", header: "ID" },
  { key: "name", header: "Name" },
  { key: "dataTruncated", header: "Data (Base64)" },
  { key: "actions", header: "Actions" },
];

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
    <DataTable
      rows={dataStores.map((dataStore, index) => ({
        id: dataStore.id || `datastore-${index}`,
        name: dataStore.name || "N/A",
        data: dataStore.data || "",
        dataTruncated: truncateData(dataStore.data),
      }))}
      headers={headers}
    >
      {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
        <TableContainer>
          <TableToolbar>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd}>Add Data Store</Button>
            </TableToolbarContent>
          </TableToolbar>
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
              {dataStores.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={headers.length} style={{ textAlign: "center", color: "var(--cds-text-secondary)" }}>
                    No data stores found. Click "Add Data Store" to upload a file.
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((row) => {
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
                              />
                              <Button
                                kind="ghost"
                                renderIcon={Download}
                                hasIconOnly
                                iconDescription="Download"
                                onClick={() => dataStore && onDownload(dataStore)}
                              />
                              <Button
                                kind="ghost"
                                renderIcon={TrashCan}
                                hasIconOnly
                                iconDescription="Delete"
                                onClick={() => dataStore?.id && onDelete(dataStore.id)}
                              />
                            </TableCell>
                          );
                        }
                        return <TableCell key={cell.id}>{cell.value}</TableCell>;
                      })}
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
