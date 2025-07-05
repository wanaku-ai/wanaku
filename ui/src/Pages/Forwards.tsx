import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import { useEffect, useState } from "react";
import { listForwards } from "../hooks/api/use-forwards";
import { ForwardReference } from "../models";
import { getNamespacePathById } from "../hooks/api/use-namespaces";

const headers = [
  { key: "name", header: "Name" },
  { key: "address", header: "Address" },
  { key: "namespace", header: "Namespace" },
];

export const Component = () => {
  const [forwards, setForwards] = useState<ForwardReference[]>([]);

  useEffect(() => {
    listForwards().then((response) => {
      if (response.data?.data) {
        const forwardsData = response.data.data.map((f: ForwardReference) => ({
          ...f,
          namespace: getNamespacePathById(f.namespace),
        }));
        setForwards(forwardsData);
      }
    });
  }, []);

  return (
    <DataTable rows={forwards} headers={headers}>
      {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
        <TableContainer
          title="Forwards"
          description="A list of forwards registered in the system."
        >
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({ header })}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow {...getRowProps({ row })}>
                  {row.cells.map((cell) => (
                    <TableCell key={cell.id}>{cell.value}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
