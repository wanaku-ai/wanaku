import {Download} from "@carbon/icons-react";
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
  Tag,
} from "@carbon/react";
import {FunctionComponent} from "react";
import {CliDownload} from "./downloads";
import {TableEmptyState} from "../EmptyTableState";

interface DownloadsTableProps {
  downloads: CliDownload[];
}

export const DownloadsTable: FunctionComponent<DownloadsTableProps> = ({downloads}) => {
  const headers = [
    {key: "name", header: "Package"},
    {key: "kind", header: "Type"},
    {key: "platform", header: "Platform"},
    {key: "runtime", header: "Runtime"},
    {key: "notes", header: "Notes"},
    {key: "download", header: "Download"},
  ];

  return (
    <DataTable headers={headers} rows={downloads.map((entry) => ({...entry, id: entry.id}))}>
      {({getTableProps, getHeaderProps, getRowProps, headers}) => (
        <TableContainer>
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({header})}>{header.header}</TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {downloads.map((entry) => (
                <TableRow {...getRowProps({row: {id: entry.id} as never})} key={entry.id}>
                  <TableCell>{entry.name}</TableCell>
                  <TableCell>
                    <Tag type={entry.kind === "java" ? "purple" : "blue"} size="sm">
                      {entry.kind === "java" ? "Java" : "Native"}
                    </Tag>
                  </TableCell>
                  <TableCell>{entry.platform}</TableCell>
                  <TableCell>{entry.runtime}</TableCell>
                  <TableCell>{entry.notes ?? "—"}</TableCell>
                  <TableCell>
                    <Button
                      kind="tertiary"
                      size="sm"
                      renderIcon={Download}
                      href={entry.downloadUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      as="a"
                    >
                      Download
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {downloads.length === 0 && (
                <TableEmptyState
                  colSpan={headers.length}
                  title="No CLI packages available"
                  body="Check the release page for the latest packages"
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
