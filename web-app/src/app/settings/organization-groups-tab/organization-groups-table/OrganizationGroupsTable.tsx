import {
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text
} from "@fluentui/react-components";
import {OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import OrganizationGroupsTableRow from "./OrganizationGroupsTableRow.tsx";
import {useOrganizationGroupsTableStyles} from "./OrganizationGroupsTableStyles.tsx";

interface OrganizationGroupsTableProps
{
    groups: OrganizationGroupDetailedDto[];
    onEdit: (group: OrganizationGroupDetailedDto) => void;
    onDelete: (group: OrganizationGroupDetailedDto) => void;
}

const OrganizationGroupsTable = ({groups, onEdit, onDelete}: OrganizationGroupsTableProps) =>
{
    const styles = useOrganizationGroupsTableStyles();

    return (
        <Table
            id="organization-groups-table"
            className={styles.table}
        >
            <TableHeader id={"organization-groups-table-table-header-1"}
                         className={styles.tableHeader}>
                <TableRow id={"organization-groups-table-table-row-1"}>
                    <TableHeaderCell id={"organization-groups-table-table-header-cell-1"}>
                        <Text id={"organization-groups-table-text-1"}
                              weight="semibold">Group name</Text>
                    </TableHeaderCell>
                    <TableHeaderCell id={"organization-groups-table-table-header-cell-2"}>
                        <Text id={"organization-groups-table-text-2"}
                              weight="semibold">Members</Text>
                    </TableHeaderCell>
                    <TableHeaderCell id={"organization-groups-table-table-header-cell-3"}
                                     className={styles.statusCell}>
                        <Text id={"organization-groups-table-text-3"}
                              weight="semibold">Status</Text>
                    </TableHeaderCell>
                    <TableHeaderCell id={"organization-groups-table-table-header-cell-4"}
                                     className={styles.actionsCell}>
                        <Text id={"organization-groups-table-text-4"}
                              weight="semibold">Actions</Text>
                    </TableHeaderCell>
                </TableRow>
            </TableHeader>
            <TableBody id={"organization-groups-table-table-body-1"}>
                {groups.map(group => (
                    <OrganizationGroupsTableRow key={group.id}
                                                group={group}
                                                onEdit={onEdit}
                                                onDelete={onDelete}/>
                ))}
                {groups.length === 0 && (
                    <TableRow id={"organization-groups-table-table-row-3"}>
                        <TableCell id={"organization-groups-table-table-cell-5"}
                                   colSpan={4}>
                            <Text id={"organization-groups-table-text-7"}>No groups match the current search and filters.</Text>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
    );
};

export default OrganizationGroupsTable;
