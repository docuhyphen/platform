import {Badge, Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, TableCell, TableRow, Text} from '@fluentui/react-components';
import {DeleteRegular, MoreHorizontalRegular, SettingsRegular} from '@fluentui/react-icons';
import {PrincipalGroupDto} from '../../../../services/types/dtos';
import {nonOwnerMembers} from '../personalGroupContacts.ts';
import GroupMemberAvatars from './GroupMemberAvatars.tsx';
import {useMyGroupsTableStyles} from './MyGroupsTableStyles.tsx';

interface MyGroupsTableRowProps
{
    group: PrincipalGroupDto;
    onManage: (group: PrincipalGroupDto) => void;
    onDelete: (group: PrincipalGroupDto) => void;
}

const MyGroupsTableRow = ({group, onManage, onDelete}: MyGroupsTableRowProps) =>
{
    const styles = useMyGroupsTableStyles();

    return (
        <TableRow id={`my-groups-tab-table-row-2-${group.id}`}>
            <TableCell id={"my-groups-tab-table-cell-1"}>
                <Text id={"my-groups-tab-text-6"}
                      weight="semibold">{group.name}</Text>
            </TableCell>
            <TableCell id={"my-groups-tab-table-cell-2"}>
                <GroupMemberAvatars members={nonOwnerMembers(group)}/>
            </TableCell>
            <TableCell id={"my-groups-tab-table-cell-3"}
                       className={styles.statusCell}>
                <Badge id={"my-groups-tab-badge-1"}
                       color={group.isActive ? 'success' : 'danger'}
                       appearance="outline">
                    {group.isActive ? 'Active' : 'Inactive'}
                </Badge>
            </TableCell>
            <TableCell id={"my-groups-tab-table-cell-4"}
                       className={styles.actionsCell}>
                <Menu>
                    <MenuTrigger disableButtonEnhancement>
                        <Button
                            id={`group-menu-${group.id}`}
                            icon={<MoreHorizontalRegular/>}
                            appearance="subtle"
                            shape={"circular"}
                        />
                    </MenuTrigger>
                    <MenuPopover id={"my-groups-tab-menu-popover-1"}>
                        <MenuList id={"my-groups-tab-menu-list-1"}>
                            <MenuItem
                                id={"my-groups-tab-menu-item-1"}
                                icon={<SettingsRegular/>}
                                onClick={() => onManage(group)}
                            >
                                Manage Group
                            </MenuItem>
                            <MenuItem
                                id={"my-groups-tab-menu-item-2"}
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

export default MyGroupsTableRow;
