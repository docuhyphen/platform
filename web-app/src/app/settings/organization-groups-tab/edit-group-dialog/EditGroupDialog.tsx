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
    Divider,
    Dropdown,
    Field,
    Input, MessageBar, MessageBarActions, MessageBarBody, MessageBarTitle,
    Option,
    Spinner,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text
} from "@fluentui/react-components";
import React, {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {fetchMyOrganizationUsers, updateOrganizationGroup} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto, OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import {useEditGroupDialogStyles} from "./EditGroupDialogStyles.tsx";
import {ArrowLeftRegular, ArrowRightRegular, DismissRegular} from "@fluentui/react-icons";
import {GroupRole, GroupRoleDisplayNames} from "../../../../services/types/roles";

interface EditGroupDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    appUserPersonOrganization: OrganizationDetailedDto;
    group: OrganizationGroupDetailedDto
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
    const [showingPermissions, setShowingPermissions] = useState(false);
    const [permissionManagementAppUser, setPermissionManagementAppUser] = useState<AppUserDetailedDto | null>(null);
    const [selectedUsers, setSelectedUsers] = useState<Map<string, {
        appUserId: string;
        allowExchangeAccept: boolean;
        allowExchangeReject: boolean;
        allowExchangeEdit: boolean;
        allowExchangeDelete: boolean;
        allowExchangeEnd: boolean;
        allowDocumentAddition: boolean;
        allowDocumentDeletion: boolean;
        allowDocumentDownload: boolean;
        allowDocumentUpdate: boolean;
        allowDocumentUpload: boolean;
    }>>(new Map());
    const [savingData, setSavingData] = useState(false);
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [permissionDenied, setPermissionDenied] = useState(false);
    const [memberRoles, setMemberRoles] = useState<Map<string, GroupRole>>(new Map());

    useEffect(() =>
    {
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
                            allowExchangeAccept: member.permissions?.allowExchangeAccept || false,
                            allowExchangeReject: member.permissions?.allowExchangeReject || false,
                            allowExchangeEdit: member.permissions?.allowExchangeEdit || false,
                            allowExchangeDelete: member.permissions?.allowExchangeDelete || false,
                            allowExchangeEnd: member.permissions?.allowExchangeEnd || false,
                            allowDocumentAddition: member.permissions?.allowDocumentAddition || false,
                            allowDocumentDeletion: member.permissions?.allowDocumentDeletion || false,
                            allowDocumentDownload: member.permissions?.allowDocumentDownload || false,
                            allowDocumentUpdate: member.permissions?.allowDocumentUpdate || false,
                            allowDocumentUpload: member.permissions?.allowDocumentUpload || false
                        });
                    }
                });

                setSelectedUsers(userMap);

                // Initialize role map from group member data
                const rolesMap = new Map<string, GroupRole>();
                group.members?.forEach((member: any) =>
                {
                    const uid = member.user?.id;
                    if (uid)
                    {
                        const role = member.groupRole || member.permissions?.groupRole || GroupRole.MEMBER;
                        rolesMap.set(uid, role as GroupRole);
                    }
                });
                setMemberRoles(rolesMap);
            })
        }
    }, [isOpen]);

    const loadUsers = async () =>
    {
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

            onClose(true)
        }
        catch (err: any)
        {
            // Backend authorizes group mutations on GROUP_MANAGE_MEMBERS / GROUP_DELETE —
            // anything else returns 403. Lock the dialog into a "read-only, no permission"
            // state so the user understands the system is refusing on purpose, not just throwing.
            const status = (err as { status?: number; response?: { status?: number } } | null | undefined)
                ?.status ?? (err as { response?: { status?: number } } | null | undefined)?.response?.status;
            if (status === 403)
            {
                setPermissionDenied(true);
                setError(null);
            }
            else
            {
                setError(err.message || "Failed to update group");
            }
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
                allowExchangeAccept: true,
                allowExchangeReject: true,
                allowExchangeEdit: true,
                allowExchangeDelete: true,
                allowExchangeEnd: true,
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

    const onClose = (complete?: boolean) =>
    {
        setShowingPermissions(false)
        setPermissionManagementAppUser(null)
        setError(null);

        if (complete)
        {
            onComplete()
        }
        else
        {
            onDismiss();
        }
    };

    const renderTable = () =>
    {
        return <>
            <Table size="small">
                <TableHeader>
                    <TableRow>
                        <TableHeaderCell>Select</TableHeaderCell>
                        <TableHeaderCell>Name</TableHeaderCell>
                        <TableHeaderCell>Email</TableHeaderCell>
                        <TableHeaderCell>Role</TableHeaderCell>
                        <TableHeaderCell>Permissions</TableHeaderCell>
                    </TableRow>
                </TableHeader>
                {renderTableBody()}
            </Table>
        </>
    }

    const renderPermissionsSection = () =>
    {
        const name = `${permissionManagementAppUser?.person.firstName} ${permissionManagementAppUser?.person.lastName}`;
        const appUserId = permissionManagementAppUser?.id?.toString() || "";
        const permissions = selectedUsers.get(appUserId);

        return <section className={styles.appUserPermissionListContainer}>
            <div>
                <Button icon={<ArrowLeftRegular/>}
                        appearance={"transparent"}
                        onClick={() => onManageAppUserPermissions(null)}/>
                Permissions for <Text weight={"semibold"}>{name}</Text>
            </div>
            <Divider appearance={"brand"}
                     alignContent={"start"}
                     className={styles.mainDivider}>
                Session Permissions
            </Divider>
            <Field>
                <Checkbox
                    label="Accept Sessions"
                    checked={permissions.allowExchangeAccept}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowExchangeAccept", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Reject Sessions"
                    checked={permissions.allowExchangeReject}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowExchangeReject", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Edit Sessions"
                    checked={permissions.allowExchangeEdit}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowExchangeEdit", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Delete Sessions"
                    checked={permissions.allowExchangeDelete}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowExchangeDelete", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="End Sessions"
                    checked={permissions.allowExchangeEnd}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowExchangeEnd", !!data.checked)}
                />
            </Field>
            <Divider appearance={"brand"}
                     alignContent={"start"}
                     className={styles.mainDivider}>
                Document Permissions
            </Divider>
            <Field>
                <Checkbox
                    label="Add Documents"
                    checked={permissions.allowDocumentAddition}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowDocumentAddition", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Delete Documents"
                    checked={permissions.allowDocumentDeletion}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowDocumentDeletion", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Download Documents"
                    checked={permissions.allowDocumentDownload}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowDocumentDownload", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Update Documents"
                    checked={permissions.allowDocumentUpdate}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowDocumentUpdate", !!data.checked)}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Upload Documents"
                    checked={permissions.allowDocumentUpload}
                    onChange={(_, data) => updateUserPermission(appUserId, "allowDocumentUpload", !!data.checked)}
                />
            </Field>
        </section>
    }

    const onManageAppUserPermissions = (appUser?: AppUserDetailedDto | null) =>
    {
        if (appUser == null)
        {
            setShowingPermissions(false);
            setPermissionManagementAppUser(null);
        }
        else
        {
            setShowingPermissions(true);
            setPermissionManagementAppUser(appUser);
        }
    }

    const renderTableBody = () =>
    {
        return <>
            <TableBody>
                {users.map((user) => (
                    <TableRow key={user.id}>
                        <TableCell
                            width={80}>
                            <Checkbox
                                checked={selectedUsers.has(user.id?.toString() || "")}
                                disabled={permissionDenied}
                                onChange={() => toggleUserSelection(user.id?.toString() || "")}
                            />
                        </TableCell>
                        <TableCell title={`${user.person?.firstName ?? ""} ${user.person?.lastName ?? ""}`.trim()}>
                            <div style={{maxWidth: "180px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap"}}>
                                {user.person?.firstName} {user.person?.lastName}
                            </div>
                        </TableCell>
                        <TableCell title={user.email}>
                            <div style={{maxWidth: "220px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap"}}>
                                {user.email}
                            </div>
                        </TableCell>
                        <TableCell>
                            {selectedUsers.has(user.id?.toString() || "") && (
                                <Dropdown
                                    size="small"
                                    disabled={permissionDenied}
                                    value={GroupRoleDisplayNames[memberRoles.get(user.id?.toString() || "") || GroupRole.MEMBER]}
                                    selectedOptions={[memberRoles.get(user.id?.toString() || "") || GroupRole.MEMBER]}
                                    onOptionSelect={(_e, d) =>
                                    {
                                        const uid = user.id?.toString() || "";
                                        setMemberRoles(prev =>
                                        {
                                            const next = new Map(prev);
                                            next.set(uid, (d.optionValue || GroupRole.MEMBER) as GroupRole);
                                            return next;
                                        });
                                    }}
                                >
                                    {Object.entries(GroupRoleDisplayNames).map(([k, v]) => (
                                        <Option key={k} value={k}>{v}</Option>
                                    ))}
                                </Dropdown>
                            )}
                        </TableCell>
                        <TableCell>
                            {selectedUsers.has(user.id?.toString() || "") && (
                                <Button size={"small"}
                                        onClick={() => onManageAppUserPermissions(user)}
                                        iconPosition={"after"}
                                        shape={"circular"}
                                        disabled={permissionDenied}
                                        icon={<ArrowRightRegular/>}
                                        appearance={"outline"}>
                                    Permissions
                                </Button>
                            )}
                        </TableCell>
                    </TableRow>
                ))}
            </TableBody>
        </>
    }

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle className={styles.dialogTitleContainer}>
                        <span> Edit Group</span>
                        <Field>
                            <Switch
                                checked={isActive}
                                onChange={(_, data) => setIsActive(data.checked)}
                                label={isActive ? "Disable" : "Enable"}
                            />
                        </Field>
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {permissionDenied &&
                            <MessageBar intent={"info"}>
                                <MessageBarBody>
                                    <MessageBarTitle>No permission</MessageBarTitle>
                                    You don't have permission to manage this group's members.
                                    Ask a group OWNER or MANAGER to make changes.
                                </MessageBarBody>
                            </MessageBar>
                        }
                        {error &&
                            <MessageBar intent={"error"}>
                                <MessageBarBody>
                                    <MessageBarTitle>Error</MessageBarTitle>
                                    {error}
                                </MessageBarBody>
                                <MessageBarActions
                                    containerAction={
                                        <Button
                                            onClick={() => setError(null)}
                                            appearance="transparent"
                                            icon={<DismissRegular/>}
                                        />
                                    }
                                />
                            </MessageBar>
                        }

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
                            ) : <>
                                {showingPermissions && renderPermissionsSection()}
                                {!showingPermissions && renderTable()}
                            </>
                            }
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={permissionDenied || savingData}
                        onClick={handleSave}>
                        {savingData && <Spinner size="tiny"/>}
                        Update Group
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            appearance="secondary"
                            shape="circular"
                            disabled={savingData}
                            onClick={onClose}>
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default EditGroupDialog;