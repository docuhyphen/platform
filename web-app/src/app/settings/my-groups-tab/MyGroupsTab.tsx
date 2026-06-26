import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
    AvatarGroup,
    AvatarGroupItem,
    AvatarGroupPopover,
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Divider,
    Field,
    Input,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    partitionAvatarGroupItems,
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
    Tooltip,
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
    MoreHorizontalRegular,
    PersonDeleteRegular,
    SettingsRegular,
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

    // Manage Group dialog (merged rename + manage members)
    const [managingGroup, setManagingGroup] = useState<PrincipalGroupDto | null>(null);
    const [manageGroupName, setManageGroupName] = useState('');
    const [manageGroupDesc, setManageGroupDesc] = useState('');
    const [savingGroupDetails, setSavingGroupDetails] = useState(false);

    // Member search (shared between create and manage dialogs)
    const [memberTagQuery, setMemberTagQuery] = useState('');
    const [memberSearchResults, setMemberSearchResults] = useState<UserContactDto[]>([]);
    const [hasUnregisteredHits, setHasUnregisteredHits] = useState(false);
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
            // Keep managingGroup in sync with refreshed data
            setManagingGroup(prev => prev ? (data.find(g => g.id === prev.id) ?? null) : null);
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

    const resetMemberPicker = () =>
    {
        setSelectedContacts([]);
        setMemberTagQuery('');
        setMemberSearchResults([]);
        setHasUnregisteredHits(false);
    };

    // ── Create ────────────────────────────────────────────────────────────
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
            resetMemberPicker();
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

    // ── Manage Group ──────────────────────────────────────────────────────
    const openManageGroup = (group: PrincipalGroupDto) =>
    {
        setManagingGroup(group);
        setManageGroupName(group.name);
        setManageGroupDesc(group.description || '');
        resetMemberPicker();
    };

    const closeManageGroup = () =>
    {
        setManagingGroup(null);
        resetMemberPicker();
    };

    const handleSaveGroupDetails = async () =>
    {
        if (!managingGroup || !manageGroupName.trim()) return;
        setSavingGroupDetails(true);
        try
        {
            await updatePersonalGroup(managingGroup.id, {
                name: manageGroupName.trim(),
                description: manageGroupDesc.trim() || undefined,
            });
            await loadGroups();
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to update group');
        }
        finally
        {
            setSavingGroupDetails(false);
        }
    };

    const onAddMembersConfirm = async () =>
    {
        if (!managingGroup || selectedContacts.length === 0) return;
        setAddingMember(true);
        try
        {
            await addPersonalGroupMembers(managingGroup.id, {
                members: selectedContacts.map(c => ({
                    principalId: c.contactAppUserId!,
                    principalKind: 'USER',
                    groupRole: 'MEMBER',
                })),
            });
            await loadGroups();
            resetMemberPicker();
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

    // ── Remove member ─────────────────────────────────────────────────────
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

    // ── Delete group ──────────────────────────────────────────────────────
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

    // ── Member tag picker (shared) ────────────────────────────────────────
    const onMemberTagQueryChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        const q = e.target.value;
        setMemberTagQuery(q);
        if (memberDebounceRef.current) clearTimeout(memberDebounceRef.current);
        if (q.length < 2)
        {
            setMemberSearchResults([]);
            setHasUnregisteredHits(false);
            return;
        }
        memberDebounceRef.current = setTimeout(async () =>
        {
            try
            {
                const results = await searchContacts(q, 10);
                const registered = results.filter(c => !!c.contactAppUserId);
                setMemberSearchResults(registered);
                setHasUnregisteredHits(results.length > registered.length);
            }
            catch
            {
                setMemberSearchResults([]);
                setHasUnregisteredHits(false);
            }
        }, 250);
    };

    const memberTagPicker = (existingMemberIds: string[] = []) => (
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
                        <Tag key={c.contactAppUserId!}
                             shape={"circular"}
                             value={c.contactAppUserId!} dismissible>
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
                    .filter(c =>
                        !selectedContacts.some(s => s.contactAppUserId === c.contactAppUserId) &&
                        !existingMemberIds.includes(c.contactAppUserId!),
                    )
                    .map(c => (
                        <TagPickerOption
                            key={c.contactAppUserId!}
                            value={c.contactAppUserId!}
                            text={[c.firstName, c.lastName].filter(Boolean).join(' ') || c.email}
                        >
                            {[c.firstName, c.lastName].filter(Boolean).join(' ')} ({c.email})
                        </TagPickerOption>
                    ))}
                {memberSearchResults.length === 0 && memberTagQuery.length >= 2 && (
                    <TagPickerOption value="__no_results__" text="no results">
                        {hasUnregisteredHits
                            ? "This contact hasn't fully registered yet and can't be added to a group"
                            : 'No registered contacts found'}
                    </TagPickerOption>
                )}
            </TagPickerList>
        </TagPicker>
    );

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
                        <span/>
                        <Button
                            icon={<AddRegular/>}
                            appearance="subtle"
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
                                <TableHeaderCell className={styles.statusCell}><Text weight="semibold">Status</Text></TableHeaderCell>
                                <TableHeaderCell className={styles.actionsCell}><Text weight="semibold">Actions</Text></TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {groups.map((group) =>
                            {
                                const nonOwners = group.members.filter(m => m.groupRole !== 'OWNER');
                                const items = nonOwners.map(m => ({
                                    name: [m.user?.person?.firstName, m.user?.person?.lastName].filter(Boolean).join(' ') || m.user?.email || 'Unknown',
                                    key: m.user?.id || m.user?.email || String(Math.random()),
                                }));
                                const {inlineItems, overflowItems} = partitionAvatarGroupItems({items, maxInlineItems: 5});
                                return (
                                    <TableRow key={group.id}>
                                        <TableCell>
                                            <Text weight="semibold">{group.name}</Text>
                                        </TableCell>
                                        <TableCell>
                                            {nonOwners.length === 0 ? (
                                                <Text size={200}>-</Text>
                                            ) : (
                                                <AvatarGroup size={24} layout="stack">
                                                    {inlineItems?.map(item => (
                                                        <Tooltip key={item.key} content={item.name} relationship="label">
                                                            <AvatarGroupItem name={item.name}/>
                                                        </Tooltip>
                                                    ))}
                                                    {overflowItems?.length > 0 && (
                                                        <AvatarGroupPopover>
                                                            {overflowItems.map(item => (
                                                                <AvatarGroupItem key={item.key} name={item.name}/>
                                                            ))}
                                                        </AvatarGroupPopover>
                                                    )}
                                                </AvatarGroup>
                                            )}
                                        </TableCell>
                                        <TableCell className={styles.statusCell}>
                                            <Badge color={group.isActive ? 'success' : 'danger'} appearance="outline">
                                                {group.isActive ? 'Active' : 'Inactive'}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className={styles.actionsCell}>
                                            <Menu>
                                                <MenuTrigger disableButtonEnhancement>
                                                    <Button icon={<MoreHorizontalRegular/>} appearance="subtle"/>
                                                </MenuTrigger>
                                                <MenuPopover>
                                                    <MenuList>
                                                        <MenuItem
                                                            icon={<SettingsRegular/>}
                                                            onClick={() => openManageGroup(group)}
                                                        >
                                                            Manage Group
                                                        </MenuItem>
                                                        <MenuItem
                                                            icon={<DeleteRegular/>}
                                                            onClick={() => setConfirmDeleteGroupId(group.id)}
                                                        >
                                                            Delete
                                                        </MenuItem>
                                                    </MenuList>
                                                </MenuPopover>
                                            </Menu>
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
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

            {/* ── Create dialog ─────────────────────────────────────────── */}
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
                                {memberTagPicker()}
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
                                resetMemberPicker();
                            }}>Cancel</Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {/* ── Manage Group dialog (merged rename + members) ─────────── */}
            <Dialog modalType="alert" open={!!managingGroup}>
                <DialogSurface style={{minWidth: 480}}>
                    <DialogBody>
                        <DialogTitle>Manage Group</DialogTitle>
                        <DialogContent style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                            <Field label="Group Name" required>
                                <Input
                                    value={manageGroupName}
                                    onChange={(_e, d) => setManageGroupName(d.value)}
                                />
                            </Field>
                            <Field label="Description">
                                <Textarea
                                    value={manageGroupDesc}
                                    onChange={(_e, d) => setManageGroupDesc(d.value)}
                                />
                            </Field>

                            <Divider/>

                            {/* Current members */}
                            <div>
                                <Text weight="semibold" size={300}>Members</Text>
                                <div style={{marginTop: 8, display: 'flex', flexDirection: 'column', gap: 2}}>
                                    {(() =>
                                    {
                                        const nonOwners = managingGroup?.members.filter(m => m.groupRole !== 'OWNER') ?? [];
                                        if (nonOwners.length === 0)
                                        {
                                            return <Text size={200} italic>No members yet.</Text>;
                                        }
                                        return nonOwners.map((m, i) => (
                                            <div key={i} style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: 8,
                                                padding: '4px 0',
                                            }}>
                                                <Text size={200} style={{flex: 1}}>
                                                    {[m.user?.person?.firstName, m.user?.person?.lastName].filter(Boolean).join(' ') || m.user?.email || 'Unknown'}
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
                                                            const name = [m.user?.person?.firstName, m.user?.person?.lastName].filter(Boolean).join(' ') || m.user?.email || 'this member';
                                                            setConfirmRemove({
                                                                groupId: managingGroup!.id,
                                                                userId: m.user.id,
                                                                displayName: name,
                                                            });
                                                        }
                                                    }}
                                                />
                                            </div>
                                        ));
                                    })()}
                                </div>
                            </div>

                            {/* Add members */}
                            <Field label="Add Members" hint="Only contacts with a registered account can be added.">
                                {memberTagPicker(
                                    managingGroup?.members
                                        .map(m => m.user?.id)
                                        .filter((id): id is string => !!id) ?? [],
                                )}
                            </Field>
                        </DialogContent>
                        <DialogActions>
                            <Button
                                appearance="primary"
                                shape="circular"
                                disabled={addingMember || selectedContacts.length === 0}
                                onClick={onAddMembersConfirm}
                            >
                                {addingMember && <Spinner size="tiny"/>} Add Members
                            </Button>
                            <Button
                                appearance="secondary"
                                shape="circular"
                                disabled={savingGroupDetails || !manageGroupName.trim()}
                                onClick={handleSaveGroupDetails}
                            >
                                {savingGroupDetails && <Spinner size="tiny"/>} Save Changes
                            </Button>
                            <Button appearance="secondary" shape="circular" onClick={closeManageGroup}>
                                Close
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {/* ── Confirm remove member ─────────────────────────────────── */}
            <Dialog modalType="alert" open={!!confirmRemove}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Remove Member</DialogTitle>
                        <DialogContent>
                            Are you sure you want to remove <strong>{confirmRemove?.displayName}</strong> from this group?
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

            {/* ── Confirm delete group ──────────────────────────────────── */}
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
                            <Button appearance="secondary" shape="circular" onClick={() => setConfirmDeleteGroupId(null)}>
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
