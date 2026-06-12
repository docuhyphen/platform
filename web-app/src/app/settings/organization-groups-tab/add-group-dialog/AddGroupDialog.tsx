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
    tokens,
} from "@fluentui/react-components";
import {
    TagPicker,
    TagPickerControl,
    TagPickerGroup,
    TagPickerInput,
    TagPickerList,
    TagPickerOption,
} from "@fluentui/react-tag-picker";
import React, {useEffect, useRef, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationGroup, fetchMyOrganizationUsers} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {useAddGroupDialogStyles} from "./AddGroupDialogStyles.tsx";
import {DeleteRegular} from "@fluentui/react-icons";
import {GroupRole, GroupRoleDisplayNames} from "../../../../services/types/roles";

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

    // TagPicker state
    const [pickerSelectedIds, setPickerSelectedIds] = useState<string[]>([]);
    const [addMemberQuery, setAddMemberQuery] = useState("");
    const [addMemberFilteredQuery, setAddMemberFilteredQuery] = useState("");
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const resetForm = () =>
    {
        setName("");
        setSelectedMembers(new Set());
        setMemberRoles(new Map());
        setPickerSelectedIds([]);
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
        setPickerSelectedIds(prev => prev.filter(id => id !== uid));
    };

    const onQueryChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        const q = e.target.value;
        setAddMemberQuery(q);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => setAddMemberFilteredQuery(q), 150);
    };

    const displayName = (u: AppUserDetailedDto) =>
        `${u.person?.firstName ?? ""} ${u.person?.lastName ?? ""}`.trim() || u.email;

    const pickerOptions = users.filter(u =>
    {
        const uid = String(u.id ?? "");
        if (selectedMembers.has(uid) || pickerSelectedIds.includes(uid)) return false;
        if (!addMemberFilteredQuery) return true;
        const q = addMemberFilteredQuery.toLowerCase();
        const fullName = `${u.person?.firstName ?? ""} ${u.person?.lastName ?? ""}`.toLowerCase();
        return fullName.includes(q) || u.email.toLowerCase().includes(q);
    });

    const memberUsers = users.filter(u => selectedMembers.has(String(u.id ?? "")));

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
                            <div style={{color: tokens.colorStatusDangerForeground1, marginBottom: "10px"}}>
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
                                        style={{marginBottom: "12px"}}
                                    >
                                        <TagPicker
                                            selectedOptions={pickerSelectedIds}
                                            onOptionSelect={(_e, data) =>
                                            {
                                                const nextIds = data.selectedOptions;
                                                const added = nextIds.filter(id => !pickerSelectedIds.includes(id));
                                                const removed = pickerSelectedIds.filter(id => !nextIds.includes(id));

                                                added.forEach(uid =>
                                                {
                                                    if (uid !== "__no_results__") addMember(uid);
                                                });
                                                removed.forEach(uid => removeMember(uid));

                                                setPickerSelectedIds(nextIds.filter(id => id !== "__no_results__"));
                                                if (added.length > 0)
                                                {
                                                    setAddMemberQuery("");
                                                    setAddMemberFilteredQuery("");
                                                }
                                            }}
                                        >
                                            <TagPickerControl>
                                                <TagPickerGroup/>
                                                <TagPickerInput
                                                    value={addMemberQuery}
                                                    onChange={onQueryChange}
                                                    placeholder="Type a name or email..."
                                                />
                                            </TagPickerControl>
                                            <TagPickerList>
                                                {pickerOptions.map(u => (
                                                    <TagPickerOption
                                                        key={String(u.id)}
                                                        value={String(u.id)}
                                                        text={displayName(u)}
                                                    >
                                                        {displayName(u)} ({u.email})
                                                    </TagPickerOption>
                                                ))}
                                                {pickerOptions.length === 0 && addMemberFilteredQuery.length >= 1 && (
                                                    <TagPickerOption value="__no_results__" text="no results">
                                                        No matching org members found
                                                    </TagPickerOption>
                                                )}
                                            </TagPickerList>
                                        </TagPicker>
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
                                                    <TableHeaderCell style={{width: 120}}>Role</TableHeaderCell>
                                                    <TableHeaderCell style={{width: 56}}>Actions</TableHeaderCell>
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
                                                                <div style={{
                                                                    maxWidth: "180px",
                                                                    overflow: "hidden",
                                                                    textOverflow: "ellipsis",
                                                                    whiteSpace: "nowrap",
                                                                }}>
                                                                    {user.person?.firstName} {user.person?.lastName}
                                                                </div>
                                                            </TableCell>
                                                            <TableCell title={user.email}>
                                                                <div style={{
                                                                    maxWidth: "220px",
                                                                    overflow: "hidden",
                                                                    textOverflow: "ellipsis",
                                                                    whiteSpace: "nowrap",
                                                                }}>
                                                                    {user.email}
                                                                </div>
                                                            </TableCell>
                                                            <TableCell style={{width: 120}}>
                                                                <Dropdown
                                                                    size="small"
                                                                    style={{minWidth: "90px", maxWidth: "110px"}}
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
                                                            <TableCell style={{width: 56}}>
                                                                <Button
                                                                    size="small"
                                                                    appearance="subtle"
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