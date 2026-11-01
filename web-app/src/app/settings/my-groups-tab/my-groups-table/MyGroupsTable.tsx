import {Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow, Text} from '@fluentui/react-components';
import {PrincipalGroupDto} from '../../../../services/types/dtos';
import MyGroupsTableRow from './MyGroupsTableRow.tsx';
import {useMyGroupsTableStyles} from './MyGroupsTableStyles.tsx';

interface MyGroupsTableProps
{
    groups: PrincipalGroupDto[];
    onManage: (group: PrincipalGroupDto) => void;
    onDelete: (group: PrincipalGroupDto) => void;
}

const MyGroupsTable = ({groups, onManage, onDelete}: MyGroupsTableProps) =>
{
    const styles = useMyGroupsTableStyles();

    return (
        <Table id={"my-groups-tab-table-1"}
               size="small"
               className={styles.table}>
            <TableHeader id={"my-groups-tab-table-header-1"}>
                <TableRow id={"my-groups-tab-table-row-1"}>
                    <TableHeaderCell id={"my-groups-tab-table-header-cell-1"}>
                        <Text id={"my-groups-tab-text-2"}
                              weight="semibold">Name</Text>
                    </TableHeaderCell>
                    <TableHeaderCell id={"my-groups-tab-table-header-cell-2"}>
                        <Text id={"my-groups-tab-text-3"}
                              weight="semibold">Members</Text>
                    </TableHeaderCell>
                    <TableHeaderCell id={"my-groups-tab-table-header-cell-3"}
                                     className={styles.statusCell}>
                        <Text id={"my-groups-tab-text-4"}
                              weight="semibold">Status</Text>
                    </TableHeaderCell>
                    <TableHeaderCell id={"my-groups-tab-table-header-cell-4"}
                                     className={styles.actionsCell}>
                        <Text id={"my-groups-tab-text-5"}
                              weight="semibold">Actions</Text>
                    </TableHeaderCell>
                </TableRow>
            </TableHeader>
            <TableBody id={"my-groups-tab-table-body-1"}>
                {groups.map(group => (
                    <MyGroupsTableRow key={group.id}
                                      group={group}
                                      onManage={onManage}
                                      onDelete={onDelete}/>
                ))}
                {groups.length === 0 && (
                    <TableRow id={"my-groups-tab-table-row-3"}>
                        <TableCell id={"my-groups-tab-table-cell-5"}
                                   colSpan={4}>
                            <Text id={"my-groups-tab-text-8"}>No personal groups yet. Create one to start organizing your contacts.</Text>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
    );
};

export default MyGroupsTable;
