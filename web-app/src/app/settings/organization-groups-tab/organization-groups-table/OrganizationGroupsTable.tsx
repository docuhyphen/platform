import {
    AvatarGroup,
    AvatarGroupPopover,
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    partitionAvatarGroupItems,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {DeleteRegular, MoreHorizontalRegular, PeopleEditRegular} from "@fluentui/react-icons";
import {OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import UserAvatarGroupItem from "../../../components/user-avatar/UserAvatarGroupItem.tsx";
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
            <TableHeader className={styles.tableHeader}>
                <TableRow>
                    <TableHeaderCell><Text weight="semibold">Group name</Text></TableHeaderCell>
                    <TableHeaderCell><Text weight="semibold">Members</Text></TableHeaderCell>
                    <TableHeaderCell className={styles.statusCell}><Text weight="semibold">Status</Text></TableHeaderCell>
                    <TableHeaderCell className={styles.actionsCell}><Text weight="semibold">Actions</Text></TableHeaderCell>
                </TableRow>
            </TableHeader>
            <TableBody>
                {groups.map(group =>
                {
                    const items = (group.members ?? []).map((member, index) => ({
                        name: [member.user?.person?.firstName, member.user?.person?.lastName]
                            .filter(Boolean)
                            .join(" ") || member.user?.email || "Unknown",
                        key: member.user?.id || member.user?.email || `${group.id}-${index}`,
                        avatarUrl: member.user?.avatarUrl
                    }));
                    const partitionedItems = partitionAvatarGroupItems({items, maxInlineItems: 8});
                    const inlineItems = partitionedItems.inlineItems ?? [];
                    const overflowItems = partitionedItems.overflowItems ?? [];

                    return (
                        <TableRow key={group.id}>
                            <TableCell title={group.name}>
                                <Text className={styles.groupName}>{group.name}</Text>
                            </TableCell>
                            <TableCell>
                                {items.length === 0
                                    ? <Text size={200}>No members</Text>
                                    : <AvatarGroup layout="stack">
                                        {inlineItems.map(item => (
                                            <Tooltip
                                                key={item.key}
                                                content={item.name}
                                                relationship="label"
                                            >
                                                <UserAvatarGroupItem
                                                    name={item.name}
                                                    avatarUrl={item.avatarUrl}
                                                />
                                            </Tooltip>
                                        ))}
                                        {overflowItems.length > 0 && (
                                            <AvatarGroupPopover>
                                                {overflowItems.map(item => (
                                                    <UserAvatarGroupItem
                                                        key={item.key}
                                                        name={item.name}
                                                        avatarUrl={item.avatarUrl}
                                                    />
                                                ))}
                                            </AvatarGroupPopover>
                                        )}
                                    </AvatarGroup>}
                            </TableCell>
                            <TableCell className={styles.statusCell}>
                                <Badge
                                    color={group.isActive ? "success" : "danger"}
                                    appearance="outline"
                                >
                                    {group.isActive ? "Active" : "Inactive"}
                                </Badge>
                            </TableCell>
                            <TableCell className={styles.actionsCell}>
                                <Menu positioning={{autoSize: true}}>
                                    <MenuTrigger disableButtonEnhancement>
                                        <Button
                                            id={`org-groups-row-actions-menu-${group.id}`}
                                            icon={<MoreHorizontalRegular/>}
                                            appearance="subtle"
                                            shape="circular"
                                        />
                                    </MenuTrigger>
                                    <MenuPopover>
                                        <MenuList>
                                            <MenuItem
                                                id={`org-groups-manage-${group.id}`}
                                                icon={<PeopleEditRegular/>}
                                                onClick={() => onEdit(group)}
                                            >
                                                Manage Group
                                            </MenuItem>
                                            <MenuItem
                                                id={`org-groups-delete-${group.id}`}
                                                icon={<DeleteRegular/>}
                                                onClick={() => onDelete(group)}
                                            >
                                                Delete
                                            </MenuItem>
                                        </MenuList>
                                    </MenuPopover>
                                </Menu>
                            </TableCell>
                        </TableRow>
                    );
                })}
                {groups.length === 0 && (
                    <TableRow>
                        <TableCell colSpan={4}>
                            <Text>No groups match the current search and filters.</Text>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
    );
};

export default OrganizationGroupsTable;
