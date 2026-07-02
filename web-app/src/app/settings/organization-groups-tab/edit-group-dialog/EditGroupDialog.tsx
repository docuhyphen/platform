import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
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
import React, {useEffect, useRef, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {fetchMyOrganizationUsers, updateOrganizationGroup} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto, OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import {useEditGroupDialogStyles} from "./EditGroupDialogStyles.tsx";
import {DeleteRegular, DismissRegular} from "@fluentui/react-icons";
import {PrincipalGroupRoleDisplayNames, PrincipalGroupRoleName} from "../../../../services/types/roles";
import MultiPersonPicker from "../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx";
import {PersonPickerItem} from "../../../components/person-picker/personPickerTypes.ts";

const toPersonPickerItem = (user: AppUserDetailedDto): PersonPickerItem => ({
    id: user.id ?? "",
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

interface EditGroupDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    appUserPersonOrganization: OrganizationDetailedDto;
    group: OrganizationGroupDetailedDto;
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
    const styles = useEditGroupDialogStyles();
    const {token} = useAuth();
    const [name, setName] = useState("");
    const [isActive, setIsActive] = useState(true);
    const [users, setUsers] = useState<AppUserDetailedDto[]>([]);
    const [selectedMembers, setSelectedMembers] = useState<Set<string>>(new Set());
    const [memberRoles, setMemberRoles] = useState<Map<string, PrincipalGroupRoleName>>(new Map());
    const [savingData, setSavingData] = useState(false);
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [permissionDenied, setPermissionDenied] = useState(false);

    // Add-member picker state
    const [addMemberQuery, setAddMemberQuery] = useState("");
    const addMemberDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const [addMemberFilteredQuery, setAddMemberFilteredQuery] = useState("");

    useEffect(() =>
    {
        if (isOpen && group)
        {
            setName(group.name || "");
            setIsActive(group.isActive);
            setPermissionDenied(false);
            setError(null);
            setAddMemberQuery("");
            setAddMemberFilteredQuery("");

            loadUsers().then((fetchedUsers) =>
            {
                const newMembers = new Set<string>();
                const rolesMap = new Map<string, PrincipalGroupRoleName>();

                group.members?.forEach((member: any) =>
                {
                    const uid = member.user?.id;
                    if (!uid) return;
                    const isFetched = fetchedUsers.some(u => u.id === uid || u.id == uid);
                    if (isFetched) newMembers.add(String(uid));
                    const role = member.groupRole || member.permissions?.groupRole || PrincipalGroupRoleName.MEMBER;
                    rolesMap.set(String(uid), role as PrincipalGroupRoleName);
                });

                setSelectedMembers(newMembers);
                setMemberRoles(rolesMap);
            });
        }
    }, [isOpen]);

    const loadUsers = async (): Promise<AppUserDetailedDto[]> =>
    {
        setLoadingUsers(true);
        try
        {
            const fetched = await fetchMyOrganizationUsers(token || undefined);
            const active = fetched.filter(u => u.isActive);
            setUsers(active);
            return active;
        }
        catch (err: any)
        {
            setError(err.message || "Failed to load users");
            console.error("Failed to load users:", err);
            return [];
        }
        finally
        {
            setLoadingUsers(false);
        }
    };

    const handleSave = async () =>
    {
        if (!appUserPersonOrganization.id || !group?.id || !name.trim()) return;

        if (selectedMembers.size === 0)
        {
            setError("Please add at least one member to the group");
            return;
        }

        const hasOwner = Array.from(selectedMembers).some(uid => memberRoles.get(uid) === PrincipalGroupRoleName.OWNER);
        if (!hasOwner)
        {
            setError("The group must have at least one member with the Owner role.");
            return;
        }

        setSavingData(true);
        setError(null);
        try
        {
            const members = Array.from(selectedMembers).map(uid => ({
                appUserId: uid,
                groupRole: memberRoles.get(uid) || PrincipalGroupRoleName.MEMBER,
            }));

            await updateOrganizationGroup(
                appUserPersonOrganization.id,
                group.id.toString(),
                {name: name.trim(), isActive, members},
                token || undefined
            );

            onClose(true);
        }
        catch (err: any)
        {
            const status = (err as any)?.status ?? (err as any)?.response?.status;
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

    const addMember = (userId: string) =>
    {
        setSelectedMembers(prev =>
        {
            const next = new Set(prev);
            next.add(userId);
            return next;
        });
        setMemberRoles(prev =>
        {
            const next = new Map(prev);
            if (!next.has(userId)) next.set(userId, PrincipalGroupRoleName.MEMBER);
            return next;
        });
    };

    const removeMember = (userId: string) =>
    {
        setSelectedMembers(prev =>
        {
            const next = new Set(prev);
            next.delete(userId);
            return next;
        });
    };

    const onClose = (complete?: boolean) =>
    {
        setError(null);
        if (complete) onComplete();
        else onDismiss();
    };

    // Options for the picker: org users not yet members and not staged in picker, filtered by query
    const pickerOptions = users.filter(u =>
    {
        const uid = String(u.id ?? "");
        if (selectedMembers.has(uid)) return false;
        if (!addMemberFilteredQuery) return true;
        const q = addMemberFilteredQuery.toLowerCase();
        const fullName = `${u.person?.firstName ?? ""} ${u.person?.lastName ?? ""}`.toLowerCase();
        return fullName.includes(q) || u.email.toLowerCase().includes(q);
    });

    const onAddMemberQueryChange = (query: string) =>
    {
        setAddMemberQuery(query);
        if (addMemberDebounceRef.current) clearTimeout(addMemberDebounceRef.current);
        addMemberDebounceRef.current = setTimeout(() => setAddMemberFilteredQuery(query), 150);
    };

    // Current members (only users that are in selectedMembers)
    const memberUsers = users.filter(u => selectedMembers.has(String(u.id ?? "")));

    const onMemberSelectionChange = (selectedIds: string[]) =>
    {
        const currentIds = Array.from(selectedMembers);
        selectedIds.filter(id => !selectedMembers.has(id)).forEach(addMember);
        currentIds.filter(id => !selectedIds.includes(id)).forEach(removeMember);
        if (selectedIds.length > currentIds.length)
        {
            setAddMemberQuery("");
            setAddMemberFilteredQuery("");
        }
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle className={styles.dialogTitleContainer}>
                        <span>Edit Group</span>
                        <Field>
                            <Switch
                                id="edit-group-dialog-active-switch"
                                checked={isActive}
                                onChange={(_, data) => setIsActive(data.checked)}
                                label={isActive ? "Disable" : "Enable"}
                            />
                        </Field>
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {permissionDenied &&
                            <MessageBar intent="info">
                                <MessageBarBody>
                                    <MessageBarTitle>No permission</MessageBarTitle>
                                    You don't have permission to manage this group's members.
                                    Ask a group OWNER or MANAGER to make changes.
                                </MessageBarBody>
                            </MessageBar>
                        }
                        {error &&
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <MessageBarTitle>Error</MessageBarTitle>
                                    {error}
                                </MessageBarBody>
                                <MessageBarActions
                                    containerAction={
                                        <Button
                                            id="edit-group-dialog-dismiss-error"
                                            onClick={() => setError(null)}
                                            appearance="transparent"
                                            shape={"circular"}
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

                        {loadingUsers ? (
                            <Spinner size="tiny" label="Loading users..."/>
                        ) : (
                            <>
                                <Field
                                    label="Add members"
                                    hint="Search by name or email to add org members."
                                    className={styles.addMembersField}
                                >
                                    <MultiPersonPicker
                                        id="edit-group-member-picker"
                                        people={pickerOptions.map(toPersonPickerItem)}
                                        selectedPeople={memberUsers.map(toPersonPickerItem)}
                                        onSelectionChange={onMemberSelectionChange}
                                        query={addMemberQuery}
                                        onQueryChange={onAddMemberQueryChange}
                                        placeholder="Type a name or email"
                                        disabled={permissionDenied}
                                        noResultsText="No matching organization members found"
                                    />
                                </Field>

                                {/* Members table */}
                                {memberUsers.length === 0 ? (
                                    <Text size={200} italic>
                                        No members added yet. Use the search above to add members.
                                    </Text>
                                ) : (
                                    <Table size="small">
                                        <TableHeader>
                                            <TableRow>
                                                <TableHeaderCell>Name</TableHeaderCell>
                                                <TableHeaderCell>Email</TableHeaderCell>
                                                <TableHeaderCell className={styles.roleHeaderCell}>Role</TableHeaderCell>
                                                <TableHeaderCell className={styles.actionsHeaderCell}>Actions</TableHeaderCell>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {memberUsers.map((user) =>
                                            {
                                                const uid = String(user.id ?? "");
                                                return (
                                                    <TableRow key={uid}>
                                                        <TableCell
                                                            title={`${user.person?.firstName ?? ""} ${user.person?.lastName ?? ""}`.trim()}>
                                                            <div className={styles.memberNameCell}>
                                                                {user.person?.firstName} {user.person?.lastName}
                                                            </div>
                                                        </TableCell>
                                                        <TableCell title={user.email}>
                                                            <div className={styles.memberEmailCell}>
                                                                {user.email}
                                                            </div>
                                                        </TableCell>
                                                        <TableCell className={styles.roleTableCell}>
                                                            <Dropdown
                                                                id={`edit-group-member-role-${uid}`}
                                                                size="small"
                                                                disabled={permissionDenied}
                                                                className={styles.roleDropdown}
                                                                value={PrincipalGroupRoleDisplayNames[memberRoles.get(uid) || PrincipalGroupRoleName.MEMBER]}
                                                                selectedOptions={[memberRoles.get(uid) || PrincipalGroupRoleName.MEMBER]}
                                                                onOptionSelect={(_e, d) =>
                                                                {
                                                                    setMemberRoles(prev =>
                                                                    {
                                                                        const next = new Map(prev);
                                                                        next.set(uid, (d.optionValue || PrincipalGroupRoleName.MEMBER) as PrincipalGroupRoleName);
                                                                        return next;
                                                                    });
                                                                }}
                                                            >
                                                                {Object.entries(PrincipalGroupRoleDisplayNames).map(([k, v]) => (
                                                                    <Option key={k} value={k}>{v}</Option>
                                                                ))}
                                                            </Dropdown>
                                                        </TableCell>
                                                        <TableCell className={styles.actionsTableCell}>
                                                            <Button
                                                                id={`edit-group-remove-member-${uid}`}
                                                                size="small"
                                                                appearance="subtle"
                                                                shape={"circular"}
                                                                icon={<DeleteRegular/>}
                                                                disabled={permissionDenied}
                                                                title="Remove member"
                                                                onClick={() => removeMember(uid)}
                                                            />
                                                        </TableCell>
                                                    </TableRow>
                                                );
                                            })}
                                        </TableBody>
                                    </Table>
                                )}
                            </>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id="edit-group-dialog-save"
                        appearance="primary"
                        shape="circular"
                        disabled={permissionDenied || savingData}
                        onClick={handleSave}>
                        {savingData && <Spinner size="tiny"/>}
                        Update Group
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id="edit-group-dialog-cancel"
                            appearance="secondary"
                            shape="circular"
                            disabled={savingData}
                            onClick={() => onClose()}>
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default EditGroupDialog;
