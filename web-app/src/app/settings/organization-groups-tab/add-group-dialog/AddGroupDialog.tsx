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
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow
} from "@fluentui/react-components";
import React, {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationGroup, fetchMyOrganizationUsers} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {useAddGroupDialogStyles} from "./AddGroupDialogStyles.tsx";

interface AddGroupDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    onComplete: () => void;
}

const AddGroupDialog: React.FC<AddGroupDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        onComplete
    }) =>
{
    const styles = useAddGroupDialogStyles()
    const {token} = useAuth();
    const [name, setName] = useState("");
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
        if (isOpen)
        {
            loadUsers();
        }
    }, [isOpen]);

    const loadUsers = async () =>
    {
        if (!organizationId) return;

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
        if (!organizationId || !name.trim()) return;

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

            await addOrganizationGroup(
                organizationId,
                {
                    name: name.trim(),
                    members
                },
                token || undefined
            );

            onComplete();
        }
        catch (err: any)
        {
            setError(err.message || "Failed to create group");
            console.error("Failed to create group:", err);
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
        setName("");
        setSelectedUsers(new Map());
        setError(null);
        onDismiss();
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Create New Group</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                        <Field label="Group Name" required>
                            <Input
                                type="text"
                                value={name}
                                maxLength={80}
                                onChange={(e) => setName(e.target.value)}
                            />
                        </Field>

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
                        disabled={savingData || !name.trim() || selectedUsers.size === 0}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        Create Group
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

export default AddGroupDialog;