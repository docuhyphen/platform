import {
    Badge,
    Button, Caption1, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger,
    Persona,
    ProgressBar,
    SearchBox,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow, Text
} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {PersonAddIcon} from "../../components/IconBundles.tsx";
import {useOrganizationPeopleTabStyles} from "./OrganizationPeopleTabStyles.tsx";
import {fetchMyOrganizationUsers} from "../../../services/organizationApi.ts";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserDetailedDto, AppUserRoleDisplayNames, OrgMemberCapacityResponse} from "../../models/models.tsx";
import {getOrgMemberCapacity} from "../../../services/authApi.ts";
import AddAppUserDialog from "./add-app-user-dialog/AddAppUserDialog.tsx";
import EditUserDialog from "./app-user-edit-dialog/EditUserDialog.tsx";
import {MoreHorizontalRegular, PersonEditRegular} from "@fluentui/react-icons";
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
    const [searchQuery, setSearchQuery] = useState("");

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

    const columns = [
        {columnKey: "person", label: "Person name"},
        {columnKey: "email", label: "Email"},
        {columnKey: "role", label: "Role"},
        {columnKey: "status", label: "Status", className: styles.statusCell},
        {columnKey: "actions", label: "Actions", className: styles.actionsCell}
    ];

    const filteredUsers = users.filter(user =>
    {
        const query = searchQuery.trim().toLocaleLowerCase();
        if (!query) return true;
        const name = [user.person?.firstName, user.person?.lastName]
            .filter(Boolean)
            .join(" ")
            .toLocaleLowerCase();
        return name.includes(query) || user.email.toLocaleLowerCase().includes(query);
    });

    return (
        <div className={styles.container}>

            {error && <div className={styles.error}>{error}</div>}

            {capacity && (
                <div className={styles.capacityBox}>
                    <div className={styles.capacityBoxRow}>
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
                        <Caption1 className={styles.capacityAtCap}>
                            Organization has reached its user limit. Upgrade your plan to add more members.
                        </Caption1>
                    )}
                    {!capacity.atCap && capacity.nearCap && (
                        <Caption1 className={styles.capacityNearCap}>
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
                    <SearchBox
                        id="organization-people-search"
                        className={styles.searchBox}
                        placeholder="Search by name or email"
                        value={searchQuery}
                        onChange={(_, data) => setSearchQuery(data.value)}
                    />
                    <Button
                        id={"org-people-add-person-btn"}
                        icon={<PersonAddIcon/>}
                        appearance="subtle"
                        shape="circular"
                        onClick={handleAddUser}>
                        Add Person
                    </Button>
                </div>
                <Table className={styles.table}>
                    <TableHeader>
                        <TableRow>
                            {columns.map((column) => (
                                <TableHeaderCell key={column.columnKey} className={column.className}>
                                    <Text weight={"semibold"}> {column.label}</Text>
                                </TableHeaderCell>
                            ))}
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {filteredUsers.map((user) => (
                            <TableRow key={user.id}>
                                <TableCell title={`${user.person?.firstName ?? ""} ${user.person?.lastName ?? ""}`.trim()}>
                                    <Persona
                                        id={`organization-person-${user.id}`}
                                        name={[user.person?.firstName, user.person?.lastName].filter(Boolean).join(" ") || user.email}
                                        secondaryText={user.email}
                                        size="small"
                                        avatar={user.avatarUrl ? {image: {src: user.avatarUrl}} : undefined}
                                    />
                                </TableCell>
                                <TableCell title={user.email}>
                                    <div className={styles.truncateCell}>{user.email}</div>
                                </TableCell>
                                <TableCell>
                                    {AppUserRoleDisplayNames[user.role as keyof typeof AppUserRoleDisplayNames] || user.role}
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
                                                appearance={"subtle"}
                                                shape={"circular"}/>
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
                onDeactivated={() =>
                {
                    setIsDeleteAppUserDialogOpen(false)
                    setSelectedUser(null);
                    loadUsers();
                }}
                onDeleted={() =>
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
