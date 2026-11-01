import {Button, Dropdown, Option, TableCell, TableRow} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {PrincipalGroupRoleDisplayNames, PrincipalGroupRoleName} from "../../../../services/types/roles";
import {GroupMembersEditorIds} from "./groupMembersEditorIds.ts";
import {useGroupMembersEditorStyles} from "./GroupMembersEditorStyles.tsx";

interface GroupMemberRowProps
{
    ids: GroupMembersEditorIds;
    user: AppUserPublicDto;
    role: PrincipalGroupRoleName;
    disabled: boolean;
    onRoleChange: (uid: string, role: PrincipalGroupRoleName) => void;
    onRemove: (uid: string) => void;
}

const GroupMemberRow = ({ids, user, role, disabled, onRoleChange, onRemove}: GroupMemberRowProps) =>
{
    const styles = useGroupMembersEditorStyles();
    const uid = user.id;

    return (
        <TableRow id={`${ids.tablePrefix}-table-row-2-${uid}`}>
            <TableCell
                id={`${ids.tablePrefix}-table-cell-1`}
                title={`${user.person?.firstName ?? ""} ${user.person?.lastName ?? ""}`.trim()}>
                <div id={ids.nameCellDiv}
                     className={styles.memberNameCell}>
                    {user.person?.firstName} {user.person?.lastName}
                </div>
            </TableCell>
            <TableCell id={`${ids.tablePrefix}-table-cell-2`}
                       title={user.email}>
                <div id={ids.emailCellDiv}
                     className={styles.memberEmailCell}>
                    {user.email}
                </div>
            </TableCell>
            <TableCell id={`${ids.tablePrefix}-table-cell-3`}
                       className={styles.roleTableCell}>
                <Dropdown
                    id={`${ids.rolePrefix}-${uid}`}
                    size="small"
                    disabled={disabled}
                    className={styles.roleDropdown}
                    value={PrincipalGroupRoleDisplayNames[role]}
                    selectedOptions={[role]}
                    onOptionSelect={(_e, d) =>
                        onRoleChange(uid, (d.optionValue || PrincipalGroupRoleName.MEMBER) as PrincipalGroupRoleName)}
                >
                    {Object.entries(PrincipalGroupRoleDisplayNames).map(([k, v]) => (
                        <Option id={`${ids.optionPrefix}-${k}`}
                                key={k}
                                value={k}>{v}</Option>
                    ))}
                </Dropdown>
            </TableCell>
            <TableCell id={`${ids.tablePrefix}-table-cell-4`}
                       className={styles.actionsTableCell}>
                <Button
                    id={`${ids.removePrefix}-${uid}`}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<DeleteRegular/>}
                    disabled={disabled}
                    title="Remove member"
                    onClick={() => onRemove(uid)}
                />
            </TableCell>
        </TableRow>
    );
};

export default GroupMemberRow;
