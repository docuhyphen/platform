import {Table, TableBody, TableHeader, TableHeaderCell, TableRow} from "@fluentui/react-components";
import {PrincipalGroupRoleName} from "../../../../services/types/roles";
import {GroupMembersEditorIds} from "./groupMembersEditorIds.ts";
import {GroupMembersSelection} from "./useGroupMembersSelection.ts";
import GroupMemberRow from "./GroupMemberRow.tsx";
import {useGroupMembersEditorStyles} from "./GroupMembersEditorStyles.tsx";

interface GroupMembersTableProps
{
    ids: GroupMembersEditorIds;
    selection: GroupMembersSelection;
    disabled: boolean;
}

const GroupMembersTable = ({ids, selection, disabled}: GroupMembersTableProps) =>
{
    const styles = useGroupMembersEditorStyles();

    return (
        <Table id={`${ids.tablePrefix}-table-1`}
               size="small">
            <TableHeader id={`${ids.tablePrefix}-table-header-1`}>
                <TableRow id={`${ids.tablePrefix}-table-row-1`}>
                    <TableHeaderCell id={`${ids.tablePrefix}-table-header-cell-1`}>Name</TableHeaderCell>
                    <TableHeaderCell id={`${ids.tablePrefix}-table-header-cell-2`}>Email</TableHeaderCell>
                    <TableHeaderCell id={`${ids.tablePrefix}-table-header-cell-3`}
                                     className={styles.roleHeaderCell}>Role</TableHeaderCell>
                    <TableHeaderCell id={`${ids.tablePrefix}-table-header-cell-4`}
                                     className={styles.actionsHeaderCell}>Actions</TableHeaderCell>
                </TableRow>
            </TableHeader>
            <TableBody id={`${ids.tablePrefix}-table-body-1`}>
                {selection.memberUsers.map(user => (
                    <GroupMemberRow key={user.id}
                                    ids={ids}
                                    user={user}
                                    role={selection.memberRoles.get(user.id) || PrincipalGroupRoleName.MEMBER}
                                    disabled={disabled}
                                    onRoleChange={selection.setMemberRole}
                                    onRemove={selection.removeMember}/>
                ))}
            </TableBody>
        </Table>
    );
};

export default GroupMembersTable;
