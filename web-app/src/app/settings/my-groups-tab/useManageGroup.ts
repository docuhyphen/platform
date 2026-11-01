import {useState} from 'react';
import {PrincipalGroupDto} from '../../../services/types/dtos';

export const useManageGroup = (groups: PrincipalGroupDto[], resetMemberPicker: () => void) =>
{
    const [managingGroupId, setManagingGroupId] = useState<string | null>(null);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const managingGroup = managingGroupId ? groups.find(g => g.id === managingGroupId) ?? null : null;

    const open = (group: PrincipalGroupDto) =>
    {
        setManagingGroupId(group.id);
        setName(group.name);
        setDescription(group.description || '');
        resetMemberPicker();
    };

    const close = () =>
    {
        setManagingGroupId(null);
        resetMemberPicker();
    };

    return {managingGroup, name, setName, description, setDescription, open, close};
};
