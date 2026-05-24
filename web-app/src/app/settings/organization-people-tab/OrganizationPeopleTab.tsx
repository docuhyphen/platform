import {
    Badge,
    Button, Caption1, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger,
    ProgressBar,
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
import {PersonAddIcon} from "../../components/IconBundles.tsx";
import {useOrganizationPeopleTabStyles} from "./OrganizationPeopleTabStyles.tsx";
import {fetchMyOrganizationUsers} from "../../../services/organizationApi.ts";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserDetailedDto, AppUserRole, AppUserRoleDisplayNames, OrgMemberCapacityResponse} from "../../models/models.tsx";
import {getOrgMemberCapacity} from "../../../services/authApi.ts";
import AddAppUserDialog from "./add-app-user-dialog/AddAppUserDialog.tsx";
import EditUserDialog from "./app-user-edit-dialog/EditUserDialog.tsx";
import {DeleteRegular, MoreHorizontalRegular, PersonEditRegular, PersonRegular} from "@fluentui/react-icons";
import AppUserDeactivateDialog from "./app-user-deactivate-dialog/AppUserDeactivateDialog.tsx";

const OrganizationPeopleTab = () =>
{
    const styles = useOrganizationPeopleTabStyles();
    const {token, appUserPersonOrganization, appUser} = useAuth();
    const [users, setUsers] = useState<AppUserDetailedDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
    const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
    const [isDeleteAppUserDialogOpen, setIsDeleteAppUserDialogOpen] = useState(false);
    const [selectedUser, setSelectedUser] = useState<AppUserDetailedDto | null>(null);
    const [capacity, setCapacity] = useState<OrgMemberCapacityResponse | null>(null);

    const loadUsers = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const fetchedUsers = await fetchMyOrganizationUsers(token || undefined);
            setUsers(fetchedUsers);
        }
        catch (err: any)
        {
            setError(err.message || "Failed to load users");
            console.error("Failed to load users:", err);
        }
        finally
        {
            setLoading(false);
        }
    };

    const loadCapacity = async () =>
    {
        if (!appUserPersonOrganization?.id) return;
        try
        {
            const cap = await getOrgMemberCapacity(appUserPersonOrganization.id);
            setCapacity(cap);
        }
        catch
        {
            // Capacity indicator is non-critical; fail silently
        }
    };

    useEffect(() =>
    {
        loadUsers();
        loadCapacity();
    }, []);

    const handleAddUser = () =>
    {
        setIsAddDialogOpen(true);
    };

    const onEditOrgAppUser = (user: AppUserDetailedDto) =>
    {
        setSelectedUser(user);
        setIsEditDialogOpen(true);
    };

    const onDeleteOrgAppUser = (user: AppUserDetailedDto) =>
    {
        setSelectedUser(user);
        setIsDeleteAppUserDialogOpen(true);
    };

    const columns = [
        {columnKey: "person", label: "Person name"},
        {columnKey: "email", label: "Email"},
        {columnKey: "role", label: "Role"},
        {columnKey: "status", label: "Status"},
        {columnKey: "actions", label: "Actions"}
    ];

    return (
        <div className={styles.container}>

            {error && <div className={styles.error}>{error}</div>}

            {capacity && (
                <div style={{padding: '12px 16px', borderRadius: '8px', border: '1px solid #e0e0e0', display: 'flex', flexDirection: 'column', gap: '6px'}}>
                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                        <Caption1>
                            <strong>Member capacity</strong> &nbsp;·&nbsp; Tier: {capacity.tierCode}
                        </Caption1>
                        <Caption1>
                            {capacity.activeUsers}{capacity.maxUsers != null ? ` / ${capacity.maxUsers}` : ' / Unlimited'}
                        </Caption1>
                    </div>
                    {capacity.maxUsers != null && (
                        <ProgressBar
                            value={capacity.activeUsers / capacity.maxUsers}
                            color={capacity.atCap ? "error" : capacity.nearCap ? "warning" : "brand"}
                            thickness="medium"
                        />
                    )}
                    {capacity.atCap && (
                        <Caption1 style={{color: 'var(--colorPaletteRedForeground1)'}}>
                            Organization has reached its user limit. Upgrade your plan to add more members.
                        </Caption1>
                    )}
                    {!capacity.atCap && capacity.nearCap && (
                        <Caption1 style={{color: 'var(--colorPaletteYellowForeground1)'}}>
                            Approaching user limit.
                        </Caption1>
                    )}
                </div>
            )}

            {loading ? (
                <div className={styles.loading}>
                    <Spinner label="Loading..."
                             size={"small"}/>
                </div>
            ) : <>
                <div className={styles.header}>
                    <div></div>
                    {/*<SearchBox className={styles.searchBox}/>*/}
                    <Button
                        icon={<PersonAddIcon/>}
                        appearance="primary"
                        shape="circular"
                        onClick={handleAddUser}>
                        Add Person
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
                        {users.map((user) => (
                            <TableRow key={user.id}>
                                <TableCell>
                                    <TableCellLayout media={<PersonRegular/>}>
                                        {user.person?.firstName} {user.person?.lastName}
                                    </TableCellLayout>
                                </TableCell>
                                <TableCell>{user.email}</TableCell>
                                <TableCell>
                                    {AppUserRoleDisplayNames[user.role as keyof typeof AppUserRoleDisplayNames] || user.role}
                                </TableCell>
                                <TableCell>
                                    <Badge
                                        color={user.isActive ? "success" : "danger"}
                                        appearance="outline"
                                    >
                                        {user.isActive ? "Active" : "Inactive"}
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
                                                <MenuItem icon={<PersonEditRegular/>}
                                                          disabled={user.id == appUser?.id}
                                                          onClick={() => onEditOrgAppUser(user)}>
                                                    Edit
                                                </MenuItem>
                                                {/*<MenuItem icon={<DeleteRegular/>}*/}
                                                {/*          disabled={user.id == appUser?.id}*/}
                                                {/*          onClick={() => onDeleteOrgAppUser(user)}>*/}
                                                {/*    Delete*/}
                                                {/*</MenuItem>*/}
                                            </MenuList>
                                        </MenuPopover>
                                    </Menu>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </>}

            <AddAppUserDialog
                isOpen={isAddDialogOpen}
                onDismiss={() => setIsAddDialogOpen(false)}
                organizationId={appUserPersonOrganization?.id}
                onComplete={() =>
                {
                    setIsAddDialogOpen(false);
                    loadUsers();
                }}
            />

            <EditUserDialog
                isOpen={isEditDialogOpen}
                onDismiss={() => setIsEditDialogOpen(false)}
                organizationId={appUserPersonOrganization?.id}
                user={selectedUser}
                onComplete={() =>
                {
                    setIsEditDialogOpen(false);
                    loadUsers();
                }}
            />

            <AppUserDeactivateDialog
                isOpen={isDeleteAppUserDialogOpen}
                onDismiss={() =>
                {
                    setSelectedUser(null);
                    setIsDeleteAppUserDialogOpen(false)
                }
                }
                appUser={selectedUser!}
                organizationId={appUserPersonOrganization?.id}
                onDeactivated={(userId) =>
                {
                    setIsDeleteAppUserDialogOpen(false)
                    setSelectedUser(null);
                    loadUsers();
                }}
                onDeleted={(userId) =>
                {
                    setIsDeleteAppUserDialogOpen(false)
                    setSelectedUser(null);
                    loadUsers();
                }}
            />

        </div>
    );
};

export default OrganizationPeopleTab;