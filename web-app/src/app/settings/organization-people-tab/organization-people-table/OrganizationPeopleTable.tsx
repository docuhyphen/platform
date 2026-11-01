import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text
} from "@fluentui/react-components";
import {MoreHorizontalRegular, PersonEditRegular} from "@fluentui/react-icons";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {OrganizationRoleDisplayNames, OrganizationRoleName} from "../../../../services/types/roles.ts";
import TagList from "../../../components/TagList.tsx";
import UserAvatar from "../../../components/user-avatar/UserAvatar.tsx";
import {useOrganizationPeopleTableStyles} from "./OrganizationPeopleTableStyles.tsx";

interface OrganizationPeopleTableProps
{
    users: AppUserPublicDto[];
    currentUserId?: string;
    onEdit: (user: AppUserPublicDto) => void;
}

const OrganizationPeopleTable = ({users, currentUserId, onEdit}: OrganizationPeopleTableProps) =>
{
    const styles = useOrganizationPeopleTableStyles();

    return (
        <Table
            id="organization-people-table"
            className={styles.table}
        >
            <TableHeader id={"organization-people-table-table-header-1"}
                         className={styles.tableHeader}>
                <TableRow id={"organization-people-table-table-row-1"}>
                    <TableHeaderCell id={"organization-people-table-table-header-cell-1"}><Text id={"organization-people-table-text-1"}
                                           weight="semibold">Person name</Text></TableHeaderCell>
                    <TableHeaderCell id={"organization-people-table-table-header-cell-2"}><Text id={"organization-people-table-text-2"}
                                           weight="semibold">Email</Text></TableHeaderCell>
                    <TableHeaderCell id={"organization-people-table-table-header-cell-3"}><Text id={"organization-people-table-text-3"}
                                           weight="semibold">Role</Text></TableHeaderCell>
                    <TableHeaderCell id={"organization-people-table-table-header-cell-4"}
                                     className={styles.statusCell}><Text id={"organization-people-table-text-4"}
                                                                         weight="semibold">Status</Text></TableHeaderCell>
                    <TableHeaderCell id={"organization-people-table-table-header-cell-5"}
                                     className={styles.actionsCell}><Text id={"organization-people-table-text-5"}
                                                                          weight="semibold">Actions</Text></TableHeaderCell>
                </TableRow>
            </TableHeader>
            <TableBody id={"organization-people-table-table-body-1"}>
                {users.map(user =>
                {
                    const name = [user.person?.firstName, user.person?.lastName].filter(Boolean).join(" ");
                    return (
                        <TableRow id={`organization-people-table-table-row-2-${user.id}`}
                                  key={user.id}>
                            <TableCell id={"organization-people-table-table-cell-1"}
                                       title={name}>
                                <div
                                    id={`organization-person-${user.id}`}
                                    className={styles.personCell}
                                >
                                    <UserAvatar
                                        id={`organization-person-avatar-${user.id}`}
                                        name={name || user.email}
                                        avatarUrl={user.avatarUrl}
                                    />
                                    <Text
                                        id={`organization-person-name-${user.id}`}
                                        className={styles.truncateCell}
                                    >
                                        {name}
                                    </Text>
                                </div>
                            </TableCell>
                            <TableCell id={"organization-people-table-table-cell-2"}
                                       title={user.email}>
                                <div id={"organization-people-table-div-1"}
                                     className={styles.truncateCell}>{user.email}</div>
                            </TableCell>
                            <TableCell id={"organization-people-table-table-cell-3"}>
                                <TagList
                                    tags={user.organizationRoles.map(role =>
                                        OrganizationRoleDisplayNames[role as OrganizationRoleName] || role)}
                                    max={1}
                                />
                            </TableCell>
                            <TableCell id={"organization-people-table-table-cell-4"}
                                       className={styles.statusCell}>
                                <Badge
                                    id={"organization-people-table-badge-1"}
                                    color={user.isActive ? "success" : "danger"}
                                    appearance="outline"
                                >
                                    {user.isActive ? "Active" : "Inactive"}
                                </Badge>
                            </TableCell>
                            <TableCell id={"organization-people-table-table-cell-5"}
                                       className={styles.actionsCell}>
                                <Menu positioning={{autoSize: true}}>
                                    <MenuTrigger disableButtonEnhancement>
                                        <Button
                                            id={`org-people-actions-menu-btn-${user.id}`}
                                            icon={<MoreHorizontalRegular/>}
                                            appearance="subtle"
                                            shape="circular"
                                        />
                                    </MenuTrigger>
                                    <MenuPopover id={"organization-people-table-menu-popover-1"}>
                                        <MenuList id={"organization-people-table-menu-list-1"}>
                                            <MenuItem
                                                id={`org-people-edit-menu-item-${user.id}`}
                                                icon={<PersonEditRegular/>}
                                                disabled={user.id === currentUserId}
                                                onClick={() => onEdit(user)}
                                            >
                                                Edit
                                            </MenuItem>
                                        </MenuList>
                                    </MenuPopover>
                                </Menu>
                            </TableCell>
                        </TableRow>
                    );
                })}
            </TableBody>
        </Table>
    );
};

export default OrganizationPeopleTable;
