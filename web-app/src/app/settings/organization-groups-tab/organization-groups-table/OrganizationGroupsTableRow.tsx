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
    TableCell,
    TableRow,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {DeleteRegular, MoreHorizontalRegular, PeopleEditRegular} from "@fluentui/react-icons";
import {OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import UserAvatarGroupItem from "../../../components/user-avatar/UserAvatarGroupItem.tsx";
import {useOrganizationGroupsTableStyles} from "./OrganizationGroupsTableStyles.tsx";

interface OrganizationGroupsTableRowProps
{
    group: OrganizationGroupDetailedDto;
    onEdit: (group: OrganizationGroupDetailedDto) => void;
    onDelete: (group: OrganizationGroupDetailedDto) => void;
}

const OrganizationGroupsTableRow = ({group, onEdit, onDelete}: OrganizationGroupsTableRowProps) =>
{
    const styles = useOrganizationGroupsTableStyles();
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
        <TableRow id={`organization-groups-table-table-row-2-${group.id}`}>
            <TableCell id={"organization-groups-table-table-cell-1"}
                       title={group.name}>
                <Text id={"organization-groups-table-text-5"}
                      className={styles.groupName}>{group.name}</Text>
            </TableCell>
            <TableCell id={"organization-groups-table-table-cell-2"}>
                {items.length === 0
                    ? <Text id={"organization-groups-table-text-6"}
                            size={200}>No members</Text>
                    : <AvatarGroup id={"organization-groups-table-avatar-group-1"}
                                   layout="stack">
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
                                        id={`organization-groups-table-user-avatar-group-item-2-${item.key}`}
                                        key={item.key}
                                        name={item.name}
                                        avatarUrl={item.avatarUrl}
                                    />
                                ))}
                            </AvatarGroupPopover>
                        )}
                    </AvatarGroup>}
            </TableCell>
            <TableCell id={"organization-groups-table-table-cell-3"}
                       className={styles.statusCell}>
                <Badge
                    id={"organization-groups-table-badge-1"}
                    color={group.isActive ? "success" : "danger"}
                    appearance="outline"
                >
                    {group.isActive ? "Active" : "Inactive"}
                </Badge>
            </TableCell>
            <TableCell id={"organization-groups-table-table-cell-4"}
                       className={styles.actionsCell}>
                <Menu positioning={{autoSize: true}}>
                    <MenuTrigger disableButtonEnhancement>
                        <Button
                            id={`org-groups-row-actions-menu-${group.id}`}
                            icon={<MoreHorizontalRegular/>}
                            appearance="subtle"
                            shape="circular"
                        />
                    </MenuTrigger>
                    <MenuPopover id={"organization-groups-table-menu-popover-1"}>
                        <MenuList id={"organization-groups-table-menu-list-1"}>
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
};

export default OrganizationGroupsTableRow;
