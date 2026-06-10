import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Tag,
    Text,
    Textarea,
} from '@fluentui/react-components';
import {
    TagPicker,
    TagPickerControl,
    TagPickerGroup,
    TagPickerInput,
    TagPickerList,
    TagPickerOption,
} from '@fluentui/react-tag-picker';
import {
    AddRegular,
    DeleteRegular,
    EditRegular,
    MoreHorizontalRegular,
    PersonAddRegular,
    PersonDeleteRegular,
} from '@fluentui/react-icons';
import {useMyGroupsTabStyles} from './MyGroupsTabStyles';
import {
    addPersonalGroupMembers,
    createPersonalGroup,
    deletePersonalGroup,
    fetchPersonalGroups,
    removePersonalGroupMember,
    updatePersonalGroup,
} from '../../../services/meGroupsApi';
import {searchContacts, UserContactDto} from '../../../services/personalContactsApi';
import {PrincipalGroupDto} from '../../../services/types/dtos';
import {GroupRoleDisplayNames} from '../../../services/types/roles';

/**
 * "My Groups" tab — personal/self-service groups.
 * Shown under Settings for all users (not org-gated).
 */
const MyGroupsTab: React.FC = () =>
{
    const styles = useMyGroupsTabStyles();
    const [groups, setGroups] = useState<PrincipalGroupDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Create dialog
    const [createOpen, setCreateOpen] = useState(false);
    const [newName, setNewName] = useState('');
    const [newDesc, setNewDesc] = useState('');
    const [creating, setCreating] = useState(false);

    // Edit dialog
    const [editGroup, setEditGroup] = useState<PrincipalGroupDto | null>(null);
    const [editName, setEditName] = useState('');
    const [editDesc, setEditDesc] = useState('');
    const [saving, setSaving] = useState(false);

    // Add member dialog (replaces inline add)
    const [addMemberDialogGroupId, setAddMemberDialogGroupId] = useState<string | null>(null);
    const [memberTagQuery, setMemberTagQuery] = useState('');
    const [memberSearchResults, setMemberSearchResults] = useState<UserContactDto[]>([]);
    const [selectedContacts, setSelectedContacts] = useState<UserContactDto[]>([]);
    const [addingMember, setAddingMember] = useState(false);
    const memberDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Remove member confirmation
    const [confirmRemove, setConfirmRemove] = useState<{groupId: string; userId: string; displayName: string} | null>(null);

    // Delete group confirmation
    const [confirmDeleteGroupId, setConfirmDeleteGroupId] = useState<string | null>(null);

    const loadGroups = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const data = await fetchPersonalGroups();
            setGroups(data);
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to load groups');
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        loadGroups();
    }, [loadGroups]);

    const handleCreate = async () =>
    {
        if (!newName.trim() || selectedContacts.length === 0) return;
        setCreating(true);
        try
        {
            const newGroup = await createPersonalGroup({name: newName.trim(), description: newDesc.trim() || undefined});
            await addPersonalGroupMembers(newGroup.id, {
                members: selectedContacts.map(c => ({
                    principalId: c.contactAppUserId!,
                    principalKind: 'USER',
                    groupRole: 'MEMBER',
                })),
            });
            setCreateOpen(false);
            setNewName('');
            setNewDesc('');
            setSelectedContacts([]);
            setMemberTagQuery('');
            setMemberSearchResults([]);
            await loadGroups();
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to create group');
        }
        finally
        {
            setCreating(false);
        }
    };

    const handleUpdate = async () =>
    {
        if (!editGroup || !editName.trim()) return;
        setSaving(true);
        try
        {
            await updatePersonalGroup(editGroup.id, {
                name: editName.trim(),
                description: editDesc.trim() || undefined,
            });
            setEditGroup(null);
            await loadGroups();
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to update group');
        }
        finally
        {
            setSaving(false);
        }
    };

    const handleDelete = async (groupId: string) =>
    {
        try
        {
            await deletePersonalGroup(groupId);
            setConfirmDeleteGroupId(null);
            await loadGroups();
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to delete group');
        }
    };

    const onMemberTagQueryChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        const q = e.target.value;
        setMemberTagQuery(q);
        if (memberDebounceRef.current) clearTimeout(memberDebounceRef.current);
        if (q.length < 2)
        {
            setMemberSearchResults([]);
            return;
        }
        memberDebounceRef.current = setTimeout(async () =>
        {
            try
            {
                const results = await searchContacts(q, 10);
                setMemberSearchResults(results.filter(c => !!c.contactAppUserId));
            }
            catch
            {
                setMemberSearchResults([]);
            }
        }, 250);
    };

    const onAddMembersConfirm = async () =>
    {
        if (!addMemberDialogGroupId || selectedContacts.length === 0) return;
        setAddingMember(true);
        try
        {
            await addPersonalGroupMembers(addMemberDialogGroupId, {
                members: selectedContacts.map(c => ({
                    principalId: c.contactAppUserId!,
                    principalKind: 'USER',
                    groupRole: 'MEMBER',
                })),
            });
            await loadGroups();
            setAddMemberDialogGroupId(null);
            setSelectedContacts([]);
            setMemberTagQuery('');
            setMemberSearchResults([]);
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to add member(s)');
        }
        finally
        {
            setAddingMember(false);
        }
    };

    const closeAddMemberDialog = () =>
    {
        setAddMemberDialogGroupId(null);
        setSelectedContacts([]);
        setMemberTagQuery('');
        setMemberSearchResults([]);
    };

    const handleRemoveMember = async (groupId: string, principalId: string) =>
    {
        try
        {
            await removePersonalGroupMember(groupId, principalId);
            setConfirmRemove(null);
            await loadGroups();
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to remove member');
        }
    };

    const openEdit = (group: PrincipalGroupDto) =>
    {
        setEditGroup(group);
        setEditName(group.name);
        setEditDesc(group.description || '');
    };

    return (
        <div className={styles.container}>
            {error && <Text className={styles.error}>{error}</Text>}

            {loading ? (
                <div className={styles.loading}>
                    <Spinner label="Loading..." size="small"/>
                </div>
            ) : (
                <>
                    <div className={styles.header}>
                        <span></span>
                        <Button
                            icon={<AddRegular/>}
                            appearance="primary"
                            shape="circular"
                            onClick={() => setCreateOpen(true)}
                        >
                            Create Group
                        </Button>
                    </div>

                    <Table size="small" className={styles.table}>
                        <TableHeader>
                            <TableRow>
                                <TableHeaderCell><Text weight="semibold">Name</Text></TableHeaderCell>
                                <TableHeaderCell><Text weight="semibold">Members</Text></TableHeaderCell>
                                <TableHeaderCell><Text weight="semibold">Status</Text></TableHeaderCell>
                                <TableHeaderCell><Text weight="semibold">Actions</Text></TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {groups.map((group) => (
                                <TableRow key={group.id}>
                                    <TableCell>
                                        <div>
                                            <Text weight="semibold">{group.name}</Text>
                                            {group.description && (
                                                <Text size={200} style={{display: 'block'}}>{group.description}</Text>
                                            )}
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        <div className={styles.memberList}>
                                            {group.members
                                                .filter(m => m.groupRole !== 'OWNER')
                                                .map((m, i) => (
                                                <div key={i} style={{display: 'flex', alignItems: 'center', gap: 4}}>
                                                    <Text size={200}>
                                                        {m.user?.email || m.user?.person?.firstName || 'Unknown'}
                                                    </Text>
                                                    <Badge size="small" appearance="outline">
                                                        {GroupRoleDisplayNames[m.groupRole as keyof typeof GroupRoleDisplayNames] || m.groupRole}
                                                    </Badge>
                                                    <Button
                                                        size="small"
                                                        appearance="subtle"
                                                        icon={<PersonDeleteRegular/>}
                                                        title="Remove member"
                                                        onClick={() =>
                                                        {
                                                            if (m.user?.id)
                                                            {
                                                                const name = m.user?.email || m.user?.person?.firstName || 'this member';
                                                                setConfirmRemove({
                                                                    groupId: group.id,
                                                                    userId: m.user.id,
                                                                    displayName: name,
                                                                });
                                                            }
                                                        }}
                                                    />
                                                </div>
                                            ))}
                                            {group.members.filter(m => m.groupRole !== 'OWNER').length === 0 && (
                                                <Button
                                                    size="small"
                                                    appearance="subtle"
                                                    icon={<PersonAddRegular/>}
                                                    onClick={() => setAddMemberDialogGroupId(group.id)}
                                                >
                                                    Add member
                                                </Button>
                                            )}
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        <Badge color={group.isActive ? 'success' : 'danger'} appearance="outline">
                                            {group.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                    </TableCell>
                                    <TableCell>
                                        <Menu>
                                            <MenuTrigger disableButtonEnhancement>
                                                <Button icon={<MoreHorizontalRegular/>} appearance="subtle"/>
                                            </MenuTrigger>
                                            <MenuPopover>
                                                <MenuList>
                                                    <MenuItem icon={<PersonAddRegular/>}
                                                              onClick={() => setAddMemberDialogGroupId(group.id)}>
                                                        Add Member
                                                    </MenuItem>
                                                    <MenuItem icon={<EditRegular/>} onClick={() => openEdit(group)}>
                                                        Rename
                                                    </MenuItem>
                                                    <MenuItem icon={<DeleteRegular/>}
                                                              onClick={() => setConfirmDeleteGroupId(group.id)}>
                                                        Delete
                                                    </MenuItem>
                                                </MenuList>
                                            </MenuPopover>
                                        </Menu>
                                    </TableCell>
                                </TableRow>
                            ))}
                            {groups.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={4}>
                                        <Text>No personal groups yet. Create one to start organizing your contacts.</Text>
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </>
            )}

            {/* Create dialog */}
            <Dialog modalType="alert" open={createOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Create Personal Group</DialogTitle>
                        <DialogContent style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                            <Field label="Group Name" required>
                                <Input value={newName} onChange={(_e, d) => setNewName(d.value)}/>
                            </Field>
                            <Field label="Description">
                                <Textarea value={newDesc} onChange={(_e, d) => setNewDesc(d.value)}/>
                            </Field>
                            <Field label="Members" required hint="Search and add at least one member to the group.">
                                <TagPicker
                                    selectedOptions={selectedContacts.map(c => c.contactAppUserId!)}
                                    onOptionSelect={(_e, data) =>
                                    {
                                        const allIds = data.selectedOptions;
                                        const updated = allIds
                                            .map(id =>
                                                selectedContacts.find(c => c.contactAppUserId === id) ||
                                                memberSearchResults.find(c => c.contactAppUserId === id),
                                            )
                                            .filter((c): c is UserContactDto => !!c);
                                        setSelectedContacts(updated);
                                    }}
                                >
                                    <TagPickerControl>
                                        <TagPickerGroup>
                                            {selectedContacts.map(c => (
                                                <Tag key={c.contactAppUserId!} value={c.contactAppUserId!} dismissible>
                                                    {[c.firstName, c.lastName].filter(Boolean).join(' ') || c.email}
                                                </Tag>
                                            ))}
                                        </TagPickerGroup>
                                        <TagPickerInput
                                            value={memberTagQuery}
                                            onChange={onMemberTagQueryChange}
                                            placeholder="Type a name or email..."
                                        />
                                    </TagPickerControl>
                                    <TagPickerList>
                                        {memberSearchResults
                                            .filter(c => !selectedContacts.some(s => s.contactAppUserId === c.contactAppUserId))
                                            .map(c => (
                                                <TagPickerOption key={c.contactAppUserId!} value={c.contactAppUserId!}>
                                                    {[c.firstName, c.lastName].filter(Boolean).join(' ')} ({c.email})
                                                </TagPickerOption>
                                            ))
                                        }
                                        {memberSearchResults.length === 0 && memberTagQuery.length >= 2 && (
                                            <TagPickerOption value="__no_results__" disabled>
                                                No registered contacts found
                                            </TagPickerOption>
                                        )}
                                    </TagPickerList>
                                </TagPicker>
                            </Field>
                        </DialogContent>
                        <DialogActions>
                            <Button
                                appearance="primary"
                                shape="circular"
                                disabled={creating || !newName.trim() || selectedContacts.length === 0}
                                onClick={handleCreate}
                            >
                                {creating && <Spinner size="tiny"/>} Create
                            </Button>
                            <Button appearance="secondary" shape="circular" onClick={() =>
                            {
                                setCreateOpen(false);
                                setNewName('');
                                setNewDesc('');
                                setSelectedContacts([]);
                                setMemberTagQuery('');
                                setMemberSearchResults([]);
                            }}>Cancel</Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {/* Edit / rename dialog */}
            <Dialog modalType="alert" open={!!editGroup}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Rename Group</DialogTitle>
                        <DialogContent style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                            <Field label="Group Name" required>
                                <Input value={editName} onChange={(_e, d) => setEditName(d.value)}/>
                            </Field>
                            <Field label="Description">
                                <Textarea value={editDesc} onChange={(_e, d) => setEditDesc(d.value)}/>
                            </Field>
                        </DialogContent>
                        <DialogActions>
                            <Button appearance="primary" shape="circular" disabled={saving || !editName.trim()}
                                    onClick={handleUpdate}>
                                {saving && <Spinner size="tiny"/>} Save
                            </Button>
                            <DialogTrigger disableButtonEnhancement>
                                <Button appearance="secondary" shape="circular"
                                        onClick={() => setEditGroup(null)}>Cancel</Button>
                            </DialogTrigger>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {/* Add Member dialog */}
            <Dialog modalType="alert" open={!!addMemberDialogGroupId}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Add Members</DialogTitle>
                        <DialogContent style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                            <Field label="Search and select members">
                                <TagPicker
                                    selectedOptions={selectedContacts.map(c => c.contactAppUserId!)}
                                    onOptionSelect={(_e, data) =>
                                    {
                                        const allIds = data.selectedOptions;
                                        const updated = allIds
                                            .map(id =>
                                                selectedContacts.find(c => c.contactAppUserId === id) ||
                                                memberSearchResults.find(c => c.contactAppUserId === id),
                                            )
                                            .filter((c): c is UserContactDto => !!c);
                                        setSelectedContacts(updated);
                                    }}
                                >
                                    <TagPickerControl>
                                        <TagPickerGroup>
                                            {selectedContacts.map(c => (
                                                <Tag
                                                    key={c.contactAppUserId!}
                                                    value={c.contactAppUserId!}
                                                    dismissible
                                                >
                                                    {[c.firstName, c.lastName].filter(Boolean).join(' ') || c.email}
                                                </Tag>
                                            ))}
                                        </TagPickerGroup>
                                        <TagPickerInput
                                            value={memberTagQuery}
                                            onChange={onMemberTagQueryChange}
                                            placeholder="Type a name or email..."
                                        />
                                    </TagPickerControl>
                                    <TagPickerList>
                                        {memberSearchResults
                                            .filter(c => !selectedContacts.some(s => s.contactAppUserId === c.contactAppUserId))
                                            .map(c => (
                                                <TagPickerOption key={c.contactAppUserId!} value={c.contactAppUserId!}>
                                                    {[c.firstName, c.lastName].filter(Boolean).join(' ')} ({c.email})
                                                </TagPickerOption>
                                            ))
                                        }
                                        {memberSearchResults.length === 0 && memberTagQuery.length >= 2 && (
                                            <TagPickerOption value="__no_results__" disabled>
                                                No registered contacts found
                                            </TagPickerOption>
                                        )}
                                    </TagPickerList>
                                </TagPicker>
                            </Field>
                            <Text size={200}>Only contacts with a registered account can be added as members.</Text>
                        </DialogContent>
                        <DialogActions>
                            <Button
                                appearance="primary"
                                shape="circular"
                                disabled={addingMember || selectedContacts.length === 0}
                                onClick={onAddMembersConfirm}
                            >
                                {addingMember && <Spinner size="tiny"/>} Add
                            </Button>
                            <Button appearance="secondary" shape="circular" onClick={closeAddMemberDialog}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {/* Confirm remove member dialog */}
            <Dialog modalType="alert" open={!!confirmRemove}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Remove Member</DialogTitle>
                        <DialogContent>
                            Are you sure you want to remove <strong>{confirmRemove?.displayName}</strong> from this
                            group?
                        </DialogContent>
                        <DialogActions>
                            <Button
                                appearance="primary"
                                shape="circular"
                                onClick={() => confirmRemove && handleRemoveMember(confirmRemove.groupId, confirmRemove.userId)}
                            >
                                Remove
                            </Button>
                            <Button appearance="secondary" shape="circular" onClick={() => setConfirmRemove(null)}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {/* Confirm delete group dialog */}
            <Dialog modalType="alert" open={!!confirmDeleteGroupId}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Delete Group</DialogTitle>
                        <DialogContent>
                            Are you sure you want to delete this group? This action cannot be undone.
                        </DialogContent>
                        <DialogActions>
                            <Button
                                appearance="primary"
                                shape="circular"
                                onClick={() => confirmDeleteGroupId && handleDelete(confirmDeleteGroupId)}
                            >
                                Delete
                            </Button>
                            <Button appearance="secondary" shape="circular"
                                    onClick={() => setConfirmDeleteGroupId(null)}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default MyGroupsTab;

