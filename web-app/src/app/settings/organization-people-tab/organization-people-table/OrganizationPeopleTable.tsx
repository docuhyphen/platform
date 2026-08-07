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
            <TableHeader className={styles.tableHeader}>
                <TableRow>
                    <TableHeaderCell><Text weight="semibold">Person name</Text></TableHeaderCell>
                    <TableHeaderCell><Text weight="semibold">Email</Text></TableHeaderCell>
                    <TableHeaderCell><Text weight="semibold">Role</Text></TableHeaderCell>
                    <TableHeaderCell className={styles.statusCell}><Text weight="semibold">Status</Text></TableHeaderCell>
                    <TableHeaderCell className={styles.actionsCell}><Text weight="semibold">Actions</Text></TableHeaderCell>
                </TableRow>
            </TableHeader>
            <TableBody>
                {users.map(user =>
                {
                    const name = [user.person?.firstName, user.person?.lastName].filter(Boolean).join(" ");
                    return (
                        <TableRow key={user.id}>
                            <TableCell title={name}>
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
                            <TableCell title={user.email}>
                                <div className={styles.truncateCell}>{user.email}</div>
                            </TableCell>
                            <TableCell>
                                <TagList
                                    tags={user.organizationRoles.map(role =>
                                        OrganizationRoleDisplayNames[role as OrganizationRoleName] || role)}
                                    max={1}
                                />
                            </TableCell>
                            <TableCell className={styles.statusCell}>
                                <Badge
                                    color={user.isActive ? "success" : "danger"}
                                    appearance="outline"
                                >
                                    {user.isActive ? "Active" : "Inactive"}
                                </Badge>
                            </TableCell>
                            <TableCell className={styles.actionsCell}>
                                <Menu positioning={{autoSize: true}}>
                                    <MenuTrigger disableButtonEnhancement>
                                        <Button
                                            id={`org-people-actions-menu-btn-${user.id}`}
                                            icon={<MoreHorizontalRegular/>}
                                            appearance="subtle"
                                            shape="circular"
                                        />
                                    </MenuTrigger>
                                    <MenuPopover>
                                        <MenuList>
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
