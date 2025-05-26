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
import {PersonAddIcon} from "../../components/IconBundles.tsx";
import {useOrganizationPeopleTabStyles} from "./OrganizationPeopleTabStyles.tsx";
import {deactivateOrganizationUser, fetchMyOrganizationUsers} from "../../../services/organizationApi.ts";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserDetailedDto, AppUserRole, AppUserRoleDisplayNames} from "../../models/models.tsx";
import AddAppUserDialog from "./add-app-user-dialog/AddAppUserDialog.tsx";
import EditUserDialog from "./app-user-edit-dialog/EditUserDialog.tsx";
import {PersonEditRegular, PersonRegular} from "@fluentui/react-icons";

const OrganizationPeopleTab = () =>
{
    const styles = useOrganizationPeopleTabStyles();
    const {token, appUserPersonOrganization, appUser} = useAuth();
    const [users, setUsers] = useState<AppUserDetailedDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
    const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
    const [selectedUser, setSelectedUser] = useState<AppUserDetailedDto | null>(null);

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

    useEffect(() =>
    {
        loadUsers();
    }, []);

    const handleAddUser = () =>
    {
        setIsAddDialogOpen(true);
    };

    const handleEditUser = (user: AppUserDetailedDto) =>
    {
        setSelectedUser(user);
        setIsEditDialogOpen(true);
    };

    const handleDeactivateUser = async (userId: string) =>
    {
        if (!window.confirm("Are you sure you want to deactivate this user?"))
        {
            return;
        }

        try
        {
            await deactivateOrganizationUser(appUserPersonOrganization?.id, userId, token || undefined);
            // Refresh user list
            loadUsers();
        }
        catch (err: any)
        {
            setError(err.message || "Failed to deactivate user");
            console.error("Failed to deactivate user:", err);
        }
    };

    const columns = [
        {columnKey: "user", label: "User"},
        {columnKey: "email", label: "Email"},
        {columnKey: "role", label: "Role"},
        {columnKey: "status", label: "Status"},
        {columnKey: "actions", label: "Actions"}
    ];

    return (
        <div className={styles.container}>

            {error && <div className={styles.error}>{error}</div>}

            {loading ? (
                <div className={styles.loading}>
                    <Spinner label="Loading..."/>
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
                                    {column.label}
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
                                <TableCell>{AppUserRoleDisplayNames[user.role as keyof typeof AppUserRoleDisplayNames] || user.role}</TableCell>
                                <TableCell>
                                    <Badge
                                        color={user.isActive ? "success" : "danger"}
                                        appearance="outline"
                                    >
                                        {user.isActive ? "Active" : "Inactive"}
                                    </Badge>
                                </TableCell>
                                <TableCell>
                                    <div className={styles.actions}>
                                        <Button
                                            icon={<PersonEditRegular/>}
                                            appearance="subtle"
                                            disabled={user.id == appUser?.id}
                                            onClick={() => handleEditUser(user)}
                                        />
                                        {user.isActive && (
                                            <Button
                                                appearance="subtle"
                                                onClick={() => handleDeactivateUser(user.id?.toString() || "")}
                                                disabled={user.id == appUser?.id}>
                                                Deactivate
                                            </Button>
                                        )}
                                    </div>
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
        </div>
    );
};

export default OrganizationPeopleTab;