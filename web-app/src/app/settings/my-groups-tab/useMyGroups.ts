import {useCallback, useEffect, useState} from 'react';
import {normalizeApiError} from '../../../utils/apiErrorUtils.ts';
import {
    addPersonalGroupMembers,
    createPersonalGroup,
    deletePersonalGroup,
    fetchPersonalGroups,
    removePersonalGroupMember,
    updatePersonalGroup,
} from '../../../services/meGroupsApi';
import {UserContactDto} from '../../../services/personalContactsApi';
import {PrincipalGroupDto} from '../../../services/types/dtos';
import {toAddMembersRequest} from './personalGroupContacts.ts';

export const useMyGroups = () =>
{
    const [groups, setGroups] = useState<PrincipalGroupDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const loadGroups = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setGroups(await fetchPersonalGroups());
        }
        catch (err: unknown)
        {
            setError(normalizeApiError(err, 'Failed to load groups').message);
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

    const run = async (action: () => Promise<unknown>, failureMessage: string): Promise<boolean> =>
    {
        try
        {
            await action();
            return true;
        }
        catch (err: unknown)
        {
            setError(normalizeApiError(err, failureMessage).message);
            return false;
        }
    };

    const createGroup = (name: string, description: string | undefined, contacts: UserContactDto[]) =>
        run(async () =>
        {
            const newGroup = await createPersonalGroup({name, description});
            await addPersonalGroupMembers(newGroup.id, toAddMembersRequest(contacts));
        }, 'Failed to create group');

    const updateGroup = (groupId: string, name: string, description: string | undefined) =>
        run(() => updatePersonalGroup(groupId, {name, description}), 'Failed to update group');

    const addMembers = (groupId: string, contacts: UserContactDto[]) =>
        run(() => addPersonalGroupMembers(groupId, toAddMembersRequest(contacts)), 'Failed to add member(s)');

    const removeMember = (groupId: string, principalId: string) =>
        run(() => removePersonalGroupMember(groupId, principalId), 'Failed to remove member');

    const deleteGroup = (groupId: string) =>
        run(() => deletePersonalGroup(groupId), 'Failed to delete group');

    return {groups, loading, error, loadGroups, createGroup, updateGroup, addMembers, removeMember, deleteGroup};
};
