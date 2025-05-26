import {
    Badge,
    Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableCellLayout,
    TableHeader,
    TableHeaderCell,
    TableRow, Text
} from "@fluentui/react-components";
import * as React from "react";
import {useEffect, useState} from "react";
import {GroupAddIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {deleteOrganizationGroup, fetchMyOrganizationGroups} from "../../../services/organizationApi.ts";
import AddGroupDialog from "./add-group-dialog/AddGroupDialog.tsx";
import EditGroupDialog from "./edit-group-dialog/EditGroupDialog.tsx";
import {
    DeleteRegular,
    EditRegular,
    GroupRegular,
    MoreHorizontalRegular,
    PeopleEditRegular, PersonEditRegular
} from "@fluentui/react-icons";
import {useOrganizationGroupTabStyles} from "./OrganizationGroupsTabStyles.tsx";
import {OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../models/models.tsx";
import GroupDeleteDialog from "./group-delete-dialog/GroupDeleteDialog.tsx";

interface OrganizationGroupsTabProps
{
    appUserPersonOrganization: OrganizationDetailedDto
}

const OrganizationGroupsTab: React.FC<OrganizationGroupsTabProps> = (
    {
        appUserPersonOrganization
    }
) =>
{
    const styles = useOrganizationGroupTabStyles();
    const {token} = useAuth();
    const [organizationId, setOrganizationId] = useState('')
    const [groups, setGroups] = useState<OrganizationGroupDetailedDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
    const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
    const [isGroupDeleteDialogOpen, setIsGroupDeleteDialogOpen] = useState(false);
    const [selectedGroup, setSelectedGroup] = useState<OrganizationGroupDetailedDto | null>(null);

    const columns = [
        {columnKey: "name", label: "Group Name"},
        {columnKey: "members", label: "Members"},
        {columnKey: "status", label: "Status"},
        {columnKey: "actions", label: "Actions"}
    ];

    const loadGroups = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const fetchedGroups = await fetchMyOrganizationGroups(token || undefined);
            setGroups(fetchedGroups);
        }
        catch (err: any)
        {
            setError(err.message || "Failed to load groups");
            console.error("Failed to load groups:", err);
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        console.log("Organization groups tab mounted with organization:", appUserPersonOrganization);

        if (appUserPersonOrganization)
        {
            setOrganizationId(appUserPersonOrganization.id!)
            loadGroups();
        }
    }, [appUserPersonOrganization]);

    const onAddGroup = () =>
    {
        setIsAddDialogOpen(true);
    };

    const onEditGroup = (group: any) =>
    {
        setSelectedGroup(group);
        setIsEditDialogOpen(true);
    };

    const onDeleteGroup = (group: OrganizationGroupDetailedDto) =>
    {
        setIsGroupDeleteDialogOpen(true)
        setSelectedGroup(group)
    }

    const renderTableRow = (group: any) =>
    {
        return (
            <TableRow key={group.id}>
                <TableCell>
                    <TableCellLayout>
                        {group.name}
                    </TableCellLayout>
                </TableCell>
                <TableCell>{group.members?.length || 0} members</TableCell>
                <TableCell>
                    <Badge
                        color={group.isActive ? "success" : "danger"}
                        appearance="outline">
                        {group.isActive ? "Active" : "Inactive"}
                    </Badge>
                </TableCell>
                <TableCell>
                    <Menu positioning={{autoSize: true}}>
                        <MenuTrigger disableButtonEnhancement>
                            <Button icon={<MoreHorizontalRegular/>}
                                    appearance={"subtle"}/>
                        </MenuTrigger>
                        <MenuPopover>
                            <MenuList>
                                <MenuItem
                                    icon={<PeopleEditRegular/>}
                                    onClick={() => onEditGroup(group)}>
                                    Edit
                                </MenuItem>
                                <MenuItem
                                    icon={<DeleteRegular/>}
                                    onClick={() => onDeleteGroup(group)}>
                                    Delete
                                </MenuItem>
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                </TableCell>
            </TableRow>
        )
    };

    const renderTable = () =>
    {
        return <>

            <div className={styles.header}>
                <div></div>
                <Button
                    icon={<GroupAddIcon/>}
                    appearance="primary"
                    shape="circular"
                    onClick={onAddGroup}>
                    Create Group
                </Button>
            </div>

            <Table className={styles.table}>
                <TableHeader>
                    <TableRow>
                        {columns.map((column) => (
                            <TableHeaderCell key={column.columnKey}>
                                <Text weight={"semibold"}> {column.label}</Text>
                            </TableHeaderCell>
                        ))}
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {groups.map((group) => renderTableRow(group))}
                </TableBody>
            </Table>
        </>
    };

    return <>
        <div className={styles.container}>
            {error && <div className={styles.error}>{error}</div>}

            {loading ? (
                <div className={styles.loading}>
                    <Spinner label="Loading..."/>
                </div>
            ) : renderTable()}

            <AddGroupDialog
                isOpen={isAddDialogOpen}
                onDismiss={() => setIsAddDialogOpen(false)}
                organizationId={organizationId}
                onComplete={() =>
                {
                    setIsAddDialogOpen(false);
                    loadGroups();
                }}
            />

            <EditGroupDialog
                isOpen={isEditDialogOpen}
                onDismiss={() => setIsEditDialogOpen(false)}
                appUserPersonOrganization={appUserPersonOrganization}
                group={selectedGroup}
                onComplete={() =>
                {
                    setIsEditDialogOpen(false);
                    loadGroups();
                }}
            />

            <GroupDeleteDialog
                isOpen={isGroupDeleteDialogOpen}
                group={selectedGroup}
                onDismiss={() => setIsGroupDeleteDialogOpen(false)}
                onDeleted={(groupId: string) =>
                {
                    loadGroups()
                }}
            />
        </div>
    </>
};

export default OrganizationGroupsTab;