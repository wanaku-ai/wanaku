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
import { Add, TrashCan } from "@carbon/icons-react";
import {useEffect, useState} from "react";
import {addForward, clearForwardsCache, listForwards, removeForward} from "../../hooks/api/use-forwards";
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
    original: ForwardReference;
}

interface ForwardsTableProps {
    rows: ForwardRow[];
    onAdd: () => void;
    onDelete: (forward: ForwardReference) => void;
}

const ForwardsTable = ({rows, onAdd, onDelete}: ForwardsTableProps) => {
    // Create a map of row id to original forward for quick lookup
    const rowDataMap = new Map(rows.map(row => [row.id, row.original]));

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
                                        <TableHeader>Actions</TableHeader>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {rows.map((row) => (
                                        <TableRow {...getRowProps({row})}>
                                            {row.cells.map((cell) => (
                                                <TableCell key={cell.id}>{cell.value}</TableCell>
                                            ))}
                                            <TableCell>
                                                <Button
                                                    kind="ghost"
                                                    renderIcon={TrashCan}
                                                    iconDescription="Delete"
                                                    hasIconOnly
                                                    onClick={() => {
                                                        const forward = rowDataMap.get(row.id);
                                                        if (forward) onDelete(forward);
                                                    }}
                                                />
                                            </TableCell>
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
                        original: f,
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
                const errorData = response.data as { error?: { message?: string } } | null;
                setErrorMessage(errorData?.error?.message || "Failed to add forward");
            }
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "An error occurred");
        }
    };

    const handleDeleteForward = async (forward: ForwardReference) => {
        try {
            const response = await removeForward(forward);
            if (response.status === 200) {
                clearForwardsCache();
                fetchForwards();
            } else {
                setErrorMessage("Failed to delete forward");
            }
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "An error occurred while deleting forward");
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
                <ForwardsTable rows={forwards} onAdd={handleAddClick} onDelete={handleDeleteForward}/>
            </div>
        </div>
    );
};

export const Component = ForwardsPage;
