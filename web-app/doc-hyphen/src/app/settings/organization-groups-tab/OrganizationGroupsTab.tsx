import {
    Badge,
    Button,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableCellLayout,
    TableHeader,
    TableHeaderCell,
    TableRow
} from "@fluentui/react-components";
import * as React from "react";
import {useEffect, useState} from "react";
import {GroupAddIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {deleteOrganizationGroup, fetchMyOrganizationGroups} from "../../../services/organizationApi.ts";
import AddGroupDialog from "./add-group-dialog/AddGroupDialog.tsx";
import EditGroupDialog from "./edit-group-dialog/EditGroupDialog.tsx";
import {DeleteRegular, EditRegular, GroupRegular} from "@fluentui/react-icons";
import {useOrganizationGroupTabStyles} from "./OrganizationGroupsTabStyles.tsx";

const OrganizationGroupsTab = () =>
{
    const styles = useOrganizationGroupTabStyles();
    const {token, appUserPersonOrganization} = useAuth();
    const [groups, setGroups] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
    const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
    const [selectedGroup, setSelectedGroup] = useState<any | null>(null);

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
        loadGroups();
    }, []);

    const onAddGroup = () =>
    {
        setIsAddDialogOpen(true);
    };

    const onEditGroup = (group: any) =>
    {
        setSelectedGroup(group);
        setIsEditDialogOpen(true);
    };

    const onDeleteGroup = async (groupId: string) =>
    {
        if (!window.confirm("Are you sure you want to delete this group?"))
        {
            return;
        }

        try
        {
            await deleteOrganizationGroup(appUserPersonOrganization?.id, groupId, token || undefined);
            // Refresh group list
            loadGroups();
        }
        catch (err: any)
        {
            setError(err.message || "Failed to delete group");
            console.error("Failed to delete group:", err);
        }
    };

    const renderTableRow = (group: any) =>
    {
        return <>
            <TableRow key={group.id}>
                <TableCell>
                    <TableCellLayout media={<GroupRegular/>}>
                        {group.name}
                    </TableCellLayout>
                </TableCell>
                <TableCell>{group.members?.length || 0} members</TableCell>
                <TableCell>
                    <Badge
                        color={group.isActive ? "success" : "danger"}
                        appearance="filled"
                    >
                        {group.isActive ? "Active" : "Inactive"}
                    </Badge>
                </TableCell>
                <TableCell>
                    <div className={styles.actions}>
                        <Button
                            icon={<EditRegular/>}
                            appearance="subtle"
                            onClick={() => onEditGroup(group)}
                        />
                        <Button
                            icon={<DeleteRegular/>}
                            appearance="subtle"
                            onClick={() => onDeleteGroup(group.id?.toString() || "")}
                        />
                    </div>
                </TableCell>
            </TableRow>
        </>
    };

    const renderTable = () =>
    {
        return <>
            <Table className={styles.table}>
                <TableHeader>
                    <TableRow>
                        {columns.map((column) => (
                            <TableHeaderCell key={column.columnKey}>
                                {column.label}
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
            <div className={styles.header}>
                <Button
                    icon={<GroupAddIcon/>}
                    appearance="primary"
                    shape="circular"
                    onClick={onAddGroup}>
                    Create Group
                </Button>
            </div>

            {error && <div className={styles.error}>{error}</div>}

            {loading ? (
                <div className={styles.loading}>
                    <Spinner label="Loading groups..."/>
                </div>
            ) : renderTable()}

            <AddGroupDialog
                isOpen={isAddDialogOpen}
                onDismiss={() => setIsAddDialogOpen(false)}
                organizationId={appUserPersonOrganization?.id}
                onComplete={() =>
                {
                    setIsAddDialogOpen(false);
                    loadGroups();
                }}
            />

            <EditGroupDialog
                isOpen={isEditDialogOpen}
                onDismiss={() => setIsEditDialogOpen(false)}
                organizationId={appUserPersonOrganization?.id}
                group={selectedGroup}
                onComplete={() =>
                {
                    setIsEditDialogOpen(false);
                    loadGroups();
                }}
            />
        </div>
    </>
};

export default OrganizationGroupsTab;