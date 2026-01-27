import {
    Button,
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
    TableToolbar,
    TableToolbarContent,
    ToastNotification,
} from "@carbon/react";
import { Add } from "@carbon/icons-react";
import {useEffect, useState} from "react";
import {addForward, clearForwardsCache, listForwards} from "../../hooks/api/use-forwards";
import {ForwardReference} from "../../models";
import {getNamespacePathById} from "../../hooks/api/use-namespaces";
import {AddForwardModal} from "./AddForwardModal";

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
    onAdd: () => void;
}

const ForwardsTable = ({rows, onAdd}: ForwardsTableProps) => {
    return (
        <Grid>
            <Column lg={12} md={8} sm={4}>
                <DataTable rows={rows} headers={headers}>
                    {({rows, headers, getTableProps, getHeaderProps, getRowProps}) => (
                        <TableContainer>
                            <TableToolbar>
                                <TableToolbarContent>
                                    <Button
                                        renderIcon={Add}
                                        onClick={onAdd}
                                    >
                                        Add Forward
                                    </Button>
                                </TableToolbarContent>
                            </TableToolbar>
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
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const fetchForwards = () => {
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
    };

    useEffect(() => {
        fetchForwards();
    }, []);

    const handleAddClick = () => {
        setIsAddModalOpen(true);
    };

    const handleAddForward = async (newForward: ForwardReference) => {
        try {
            const response = await addForward(newForward);
            if (response.status === 200) {
                setIsAddModalOpen(false);
                clearForwardsCache();
                fetchForwards();
            } else {
                setErrorMessage(response.data?.message || "Failed to add forward");
            }
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "An error occurred");
        }
    };

    return (
        <div>
            <h1 className="title">Forwards</h1>
            <p className="description">
                A list of forwards registered in the system.
            </p>
            {errorMessage && (
                <ToastNotification
                    kind="error"
                    title="Error"
                    subtitle={errorMessage}
                    onCloseButtonClick={() => setErrorMessage(null)}
                    timeout={5000}
                />
            )}
            {isAddModalOpen && (
                <AddForwardModal
                    onRequestClose={() => setIsAddModalOpen(false)}
                    onSubmit={handleAddForward}
                />
            )}
            <div id="page-content">
                <ForwardsTable rows={forwards} onAdd={handleAddClick}/>
            </div>
        </div>
    );
};

export const Component = ForwardsPage;
