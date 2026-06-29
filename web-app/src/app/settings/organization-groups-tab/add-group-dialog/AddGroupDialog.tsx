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
    Input,
    Option,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import React, {useEffect, useRef, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationGroup, fetchMyOrganizationUsers} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {useAddGroupDialogStyles} from "./AddGroupDialogStyles.tsx";
import {DeleteRegular} from "@fluentui/react-icons";
import {GroupRole, GroupRoleDisplayNames} from "../../../../services/types/roles";
import MultiPersonPicker from "../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx";
import {PersonPickerItem} from "../../../components/person-picker/personPickerTypes.ts";

const toPersonPickerItem = (user: AppUserDetailedDto): PersonPickerItem => ({
    id: user.id ?? "",
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

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
    const styles = useAddGroupDialogStyles();
    const {token, appUser} = useAuth();
    const [name, setName] = useState("");
    const [users, setUsers] = useState<AppUserDetailedDto[]>([]);
    const [selectedMembers, setSelectedMembers] = useState<Set<string>>(new Set());
    const [memberRoles, setMemberRoles] = useState<Map<string, GroupRole>>(new Map());
    const [savingData, setSavingData] = useState(false);
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [addMemberQuery, setAddMemberQuery] = useState("");
    const [addMemberFilteredQuery, setAddMemberFilteredQuery] = useState("");
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const resetForm = () =>
    {
        setName("");
        setSelectedMembers(new Set());
        setMemberRoles(new Map());
        setAddMemberQuery("");
        setAddMemberFilteredQuery("");
        setError(null);
    };

    useEffect(() =>
    {
        if (isOpen)
        {
            resetForm();
            loadUsers();
        }
    }, [isOpen]);

    const loadUsers = async () =>
    {
        if (!organizationId) return;
        setLoadingUsers(true);
        try
        {
            const fetched = await fetchMyOrganizationUsers(token || undefined);
            setUsers(fetched.filter(u => u.isActive));
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
        if (selectedMembers.size === 0)
        {
            setError("Please add at least one member to the group");
            return;
        }

        const hasOwner = Array.from(selectedMembers).some(uid => memberRoles.get(uid) === GroupRole.OWNER);
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
                groupRole: memberRoles.get(uid) || GroupRole.MEMBER,
            }));
            await addOrganizationGroup(organizationId, {name: name.trim(), members}, token || undefined);
            resetForm();
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

    const addMember = (uid: string) =>
    {
        setSelectedMembers(prev =>
        {
            const next = new Set(prev);
            next.add(uid);
            return next;
        });
        setMemberRoles(prev =>
        {
            const next = new Map(prev);
            if (!next.has(uid))
                next.set(uid, uid === appUser?.id ? GroupRole.OWNER : GroupRole.MEMBER);
            return next;
        });
    };

    const removeMember = (uid: string) =>
    {
        setSelectedMembers(prev =>
        {
            const next = new Set(prev);
            next.delete(uid);
            return next;
        });
    };

    const onQueryChange = (query: string) =>
    {
        setAddMemberQuery(query);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => setAddMemberFilteredQuery(query), 150);
    };

    const pickerOptions = users.filter(u =>
    {
        const uid = String(u.id ?? "");
        if (selectedMembers.has(uid)) return false;
        if (!addMemberFilteredQuery) return true;
        const q = addMemberFilteredQuery.toLowerCase();
        const fullName = `${u.person?.firstName ?? ""} ${u.person?.lastName ?? ""}`.toLowerCase();
        return fullName.includes(q) || u.email.toLowerCase().includes(q);
    });

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

    const onClose = () =>
    {
        resetForm();
        onDismiss();
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Create New Group</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {error && (
                            <div className={styles.errorMessage}>
                                {error}
                            </div>
                        )}

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
                                <>
                                    {/* Search picker at the top */}
                                    <Field
                                        label="Add members"
                                        hint="Search by name or email to add org members."
                                        className={styles.addMembersField}
                                    >
                                        <MultiPersonPicker
                                            id="add-group-member-picker"
                                            people={pickerOptions.map(toPersonPickerItem)}
                                            selectedPeople={memberUsers.map(toPersonPickerItem)}
                                            onSelectionChange={onMemberSelectionChange}
                                            query={addMemberQuery}
                                            onQueryChange={onQueryChange}
                                            placeholder="Type a name or email"
                                            noResultsText="No matching organization members found"
                                        />
                                    </Field>

                                    {/* Members-only table */}
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
                                                {memberUsers.map(user =>
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
                                                                    id={`add-group-member-role-${uid}`}
                                                                    size="small"
                                                                    className={styles.roleDropdown}
                                                                    value={GroupRoleDisplayNames[memberRoles.get(uid) || GroupRole.MEMBER]}
                                                                    selectedOptions={[memberRoles.get(uid) || GroupRole.MEMBER]}
                                                                    onOptionSelect={(_e, d) =>
                                                                    {
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
                                                            </TableCell>
                                                            <TableCell className={styles.actionsTableCell}>
                                                                <Button
                                                                    id={`add-group-remove-member-${uid}`}
                                                                    size="small"
                                                                    appearance="subtle"
                                                                    shape={"circular"}
                                                                    icon={<DeleteRegular/>}
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
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id="add-group-dialog-create"
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !name.trim() || selectedMembers.size === 0}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        Create Group
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id="add-group-dialog-cancel"
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
