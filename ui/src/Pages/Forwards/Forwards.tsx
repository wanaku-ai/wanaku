import {
    Column,
    DataTable,
    Grid,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableHeader,
    TableRow,
} from "@carbon/react";
import {useEffect, useState} from "react";
import {listForwards} from "../../hooks/api/use-forwards";
import {ForwardReference} from "../../models";
import {getNamespacePathById} from "../../hooks/api/use-namespaces";

const headers = [
    {key: "name", header: "Name"},
    {key: "address", header: "Address"},
    {key: "namespace", header: "Namespace"},
];

interface ForwardRow {
    id: string;
    name?: string;
    address?: string;
    namespace?: string;
}

interface ForwardsTableProps {
    rows: ForwardRow[];
}

const ForwardsTable = ({rows}: ForwardsTableProps) => {
    return (
        <Grid>
            <Column lg={12} md={8} sm={4}>
                <div
                    style={{
                        display: "flex",
                        justifyContent: "flex-end",
                        alignItems: "center",
                    }}
                >
                </div>
                <DataTable rows={rows} headers={headers}>
                    {({rows, headers, getTableProps, getHeaderProps, getRowProps}) => (
                        <TableContainer>
                               <Table {...getTableProps()}>
                                <TableHead>
                                    <TableRow>
                                        {headers.map((header) => (
                                            <TableHeader {...getHeaderProps({header})}>
                                                {header.header}
                                            </TableHeader>
                                        ))}
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {rows.map((row) => (
                                        <TableRow {...getRowProps({row})}>
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
            </Column>
        </Grid>
    );
};

const ForwardsPage = () => {
    const [forwards, setForwards] = useState<ForwardRow[]>([]);

    useEffect(() => {
        listForwards().then((response) => {
            if (response.data?.data) {
                const forwardsData: ForwardRow[] = response.data.data
                    .filter((f: ForwardReference) => f.id)
                    .map((f: ForwardReference) => ({
                        id: f.id!,
                        name: f.name,
                        address: f.address,
                        namespace: getNamespacePathById(f.namespace),
                    }));
                setForwards(forwardsData);
            }
        });
    }, []);

    return (
        <div>
            <h1 className="title">Forwards</h1>
            <p className="description">
                A list of forwards registered in the system.
            </p>
            <div id="page-content">
                <ForwardsTable rows={forwards}/>
            </div>
        </div>
    );
};

export const Component = ForwardsPage;
