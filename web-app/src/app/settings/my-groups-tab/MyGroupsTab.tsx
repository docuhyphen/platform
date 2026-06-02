import React, {useCallback, useEffect, useState} from 'react';
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
    Text,
    Textarea,
} from '@fluentui/react-components';
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
import {searchContacts} from '../../../services/personalContactsApi';
import {PrincipalGroupDto} from '../../../services/types/dtos';
import {GroupRoleDisplayNames} from '../../../services/types/roles';

/**
 * "My Groups" tab — personal/self-service groups (Plan 02).
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

    // Add member
    const [addMemberGroupId, setAddMemberGroupId] = useState<string | null>(null);
    const [memberQuery, setMemberQuery] = useState('');
    const [memberSearchResults, setMemberSearchResults] = useState<any[]>([]);
    const [addingMember, setAddingMember] = useState(false);

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
        if (!newName.trim()) return;
        setCreating(true);
        try
        {
            await createPersonalGroup({name: newName.trim(), description: newDesc.trim() || undefined});
            setCreateOpen(false);
            setNewName('');
            setNewDesc('');
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
            await loadGroups();
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to delete group');
        }
    };

    const handleSearchMembers = async (query: string) =>
    {
        setMemberQuery(query);
        if (query.length < 2) { setMemberSearchResults([]); return; }
        try
        {
            const results = await searchContacts(query, 10);
            setMemberSearchResults(results);
        }
        catch
        {
            setMemberSearchResults([]);
        }
    };

    const handleAddMember = async (contactAppUserId: string) =>
    {
        if (!addMemberGroupId || !contactAppUserId) return;
        setAddingMember(true);
        try
        {
            await addPersonalGroupMembers(addMemberGroupId, {
                members: [{principalId: contactAppUserId, principalKind: 'USER', groupRole: 'MEMBER'}],
            });
            await loadGroups();
            setMemberQuery('');
            setMemberSearchResults([]);
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to add member');
        }
        finally
        {
            setAddingMember(false);
        }
    };

    const handleRemoveMember = async (groupId: string, principalId: string) =>
    {
        try
        {
            await removePersonalGroupMember(groupId, principalId);
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
                        <Text weight="semibold" size={500}>My Groups</Text>
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
                                            {group.members.map((m, i) => (
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
                                                        onClick={() => m.user?.id && handleRemoveMember(group.id, m.user.id)}
                                                    />
                                                </div>
                                            ))}
                                            {/* Inline add member */}
                                            {addMemberGroupId === group.id ? (
                                                <div style={{display: 'flex', gap: 4, alignItems: 'center'}}>
                                                    <Input
                                                        size="small"
                                                        placeholder="Search contacts..."
                                                        value={memberQuery}
                                                        onChange={(_e, d) => handleSearchMembers(d.value)}
                                                    />
                                                    {addingMember && <Spinner size="tiny"/>}
                                                    <Button size="small" appearance="subtle"
                                                            onClick={() => { setAddMemberGroupId(null); setMemberSearchResults([]); setMemberQuery(''); }}>
                                                        Cancel
                                                    </Button>
                                                    {memberSearchResults.length > 0 && (
                                                        <div style={{position: 'absolute', zIndex: 10, background: 'var(--colorNeutralBackground1)', border: '1px solid var(--colorNeutralStroke1)', borderRadius: 4, marginTop: 24}}>
                                                            {memberSearchResults.map((c) => (
                                                                <Button
                                                                    key={c.contactAppUserId || c.email}
                                                                    size="small"
                                                                    appearance="subtle"
                                                                    style={{display: 'block', width: '100%', textAlign: 'left'}}
                                                                    disabled={!c.contactAppUserId}
                                                                    onClick={() => c.contactAppUserId && handleAddMember(c.contactAppUserId)}
                                                                >
                                                                    {c.firstName} {c.lastName} ({c.email})
                                                                </Button>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                            ) : (
                                                <Button
                                                    size="small"
                                                    appearance="subtle"
                                                    icon={<PersonAddRegular/>}
                                                    onClick={() => setAddMemberGroupId(group.id)}
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
                                                    <MenuItem icon={<EditRegular/>} onClick={() => openEdit(group)}>
                                                        Rename
                                                    </MenuItem>
                                                    <MenuItem icon={<DeleteRegular/>} onClick={() => handleDelete(group.id)}>
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
                        </DialogContent>
                        <DialogActions>
                            <Button appearance="primary" shape="circular" disabled={creating || !newName.trim()} onClick={handleCreate}>
                                {creating && <Spinner size="tiny"/>} Create
                            </Button>
                            <DialogTrigger disableButtonEnhancement>
                                <Button appearance="secondary" shape="circular" onClick={() => setCreateOpen(false)}>Cancel</Button>
                            </DialogTrigger>
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
                            <Button appearance="primary" shape="circular" disabled={saving || !editName.trim()} onClick={handleUpdate}>
                                {saving && <Spinner size="tiny"/>} Save
                            </Button>
                            <DialogTrigger disableButtonEnhancement>
                                <Button appearance="secondary" shape="circular" onClick={() => setEditGroup(null)}>Cancel</Button>
                            </DialogTrigger>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default MyGroupsTab;

