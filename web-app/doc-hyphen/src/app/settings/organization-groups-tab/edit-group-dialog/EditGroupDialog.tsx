import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow
} from "@fluentui/react-components";
import React, {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {fetchMyOrganizationUsers, updateOrganizationGroup} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto, OrganizationDetailedDto} from "../../../models/models.tsx";
import {useEditGroupDialogStyles} from "./EditGroupDialogStyles.tsx";

interface EditGroupDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    appUserPersonOrganization: OrganizationDetailedDto;
    group: any | null; // Replace with actual type when available
    onComplete: () => void;
}

const EditGroupDialog: React.FC<EditGroupDialogProps> = (
    {
        isOpen,
        onDismiss,
        appUserPersonOrganization,
        group,
        onComplete
    }) =>
{
    const styles = useEditGroupDialogStyles()

    const {token} = useAuth();
    const [name, setName] = useState("");
    const [isActive, setIsActive] = useState(true);
    const [users, setUsers] = useState<AppUserDetailedDto[]>([]);
    const [selectedUsers, setSelectedUsers] = useState<Map<string, {
        appUserId: string;
        allowSessionAccept: boolean;
        allowSessionReject: boolean;
        allowSessionEdit: boolean;
        allowSessionDelete: boolean;
        allowSessionEnd: boolean;
        allowDocumentAddition: boolean;
        allowDocumentDeletion: boolean;
        allowDocumentDownload: boolean;
        allowDocumentUpdate: boolean;
        allowDocumentUpload: boolean;
    }>>(new Map());
    const [savingData, setSavingData] = useState(false);
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        console.log("EditGroupDialog useEffect triggered", users)
        if (isOpen && group)
        {
            setName(group.name || "");
            setIsActive(group.isActive);

            loadUsers().then(() =>
            {
                const userMap = new Map();

                group.members?.forEach((member: any) =>
                {
                    const appUserIsMember = users.find(u => u.id == member.user.id)

                    if (appUserIsMember)
                    {
                        const appUserMember = member.user
                        userMap.set(appUserMember.id, {
                            appUserId: appUserMember.id,
                            allowSessionAccept: member.permissions?.allowSessionAccept || false,
                            allowSessionReject: member.permissions?.allowSessionReject || false,
                            allowSessionEdit: member.permissions?.allowSessionEdit || false,
                            allowSessionDelete: member.permissions?.allowSessionDelete || false,
                            allowSessionEnd: member.permissions?.allowSessionEnd || false,
                            allowDocumentAddition: member.permissions?.allowDocumentAddition || false,
                            allowDocumentDeletion: member.permissions?.allowDocumentDeletion || false,
                            allowDocumentDownload: member.permissions?.allowDocumentDownload || false,
                            allowDocumentUpdate: member.permissions?.allowDocumentUpdate || false,
                            allowDocumentUpload: member.permissions?.allowDocumentUpload || false
                        });
                    }
                });

                setSelectedUsers(userMap);
            })
        }
    }, [isOpen]);

    const loadUsers = async () =>
    {
        console.log("Loading users for org: organizationId", appUserPersonOrganization.id)

        setLoadingUsers(true);
        try
        {
            const fetchedUsers = await fetchMyOrganizationUsers(token || undefined);
            setUsers(fetchedUsers.filter(user => user.isActive));
        }
        catch (err: any)
        {
            setError(err.message || "Failed to load users");
            console.error("Failed to load users:", err);
        }
        finally
        {
            setLoadingUsers(false);
        }
    };

    const handleSave = async () =>
    {
        if (!appUserPersonOrganization.id || !group?.id || !name.trim()) return;

        if (selectedUsers.size === 0)
        {
            setError("Please select at least one member for the group");
            return;
        }

        setSavingData(true);
        setError(null);

        try
        {
            const members = Array.from(selectedUsers.values());

            await updateOrganizationGroup(
                appUserPersonOrganization.id,
                group.id.toString(),
                {
                    name: name.trim(),
                    isActive,
                    members
                },
                token || undefined
            );

            onComplete();
        }
        catch (err: any)
        {
            setError(err.message || "Failed to update group");
            console.error("Failed to update group:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const toggleUserSelection = (userId: string) =>
    {
        const newSelectedUsers = new Map(selectedUsers);

        if (newSelectedUsers.has(userId))
        {
            newSelectedUsers.delete(userId);
        }
        else
        {
            newSelectedUsers.set(userId, {
                appUserId: userId,
                allowSessionAccept: true,
                allowSessionReject: true,
                allowSessionEdit: true,
                allowSessionDelete: true,
                allowSessionEnd: true,
                allowDocumentAddition: true,
                allowDocumentDeletion: true,
                allowDocumentDownload: true,
                allowDocumentUpdate: true,
                allowDocumentUpload: true
            });
        }

        setSelectedUsers(newSelectedUsers);
    };

    const updateUserPermission = (userId: string, permission: string, value: boolean) =>
    {
        const userPermissions = selectedUsers.get(userId);
        if (!userPermissions) return;

        const newSelectedUsers = new Map(selectedUsers);
        newSelectedUsers.set(userId, {
            ...userPermissions,
            [permission]: value
        });

        setSelectedUsers(newSelectedUsers);
    };

    const onClose = () =>
    {
        setError(null);
        onDismiss();
    };

    const hasChanges =
        name !== group?.name ||
        isActive !== group?.isActive ||
        // Compare members (simplified check)
        selectedUsers.size !== (group?.members?.length || 0);

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Edit Group</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                        <div className={styles.nameSection}>
                            <Field label="Group Name" required>
                                <Input
                                    type="text"
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    checked={isActive}
                                    onChange={(_, data) => setIsActive(data.checked)}
                                    label={isActive ? "Disable" : "Enable"}
                                />
                            </Field>
                        </div>

                        <Field label="Group Members" required>
                            {loadingUsers ? (
                                <Spinner size="tiny" label="Loading users..."/>
                            ) : (
                                <Table size="small">
                                    <TableHeader>
                                        <TableRow>
                                            <TableHeaderCell>Select</TableHeaderCell>
                                            <TableHeaderCell>Name</TableHeaderCell>
                                            <TableHeaderCell>Email</TableHeaderCell>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {users.map((user) => (
                                            <TableRow key={user.id}>
                                                <TableCell>
                                                    <Checkbox
                                                        checked={selectedUsers.has(user.id?.toString() || "")}
                                                        onChange={() => toggleUserSelection(user.id?.toString() || "")}
                                                    />
                                                </TableCell>
                                                <TableCell>{user.person?.firstName} {user.person?.lastName}</TableCell>
                                                <TableCell>{user.email}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !name.trim() || selectedUsers.size === 0 || !hasChanges}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        Update Group
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            appearance="secondary"
                            shape="circular"
                            disabled={savingData}
                            onClick={onClose}
                        >
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default EditGroupDialog;