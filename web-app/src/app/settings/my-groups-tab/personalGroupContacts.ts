import {UserContactDto} from '../../../services/personalContactsApi';
import {AddGroupMembersRequest, PrincipalGroupDto, PrincipalGroupMemberDto} from '../../../services/types/dtos';
import {PrincipalGroupRoleName} from '../../../services/types/roles';
import {PersonPickerItem} from '../../components/person-picker/personPickerTypes.ts';

export const toPersonPickerItem = (contact: UserContactDto): PersonPickerItem => ({
    id: contact.contactAppUserId ?? contact.email,
    email: contact.email,
    firstName: contact.firstName,
    lastName: contact.lastName,
    avatarUrl: contact.avatarUrl,
});

export const toAddMembersRequest = (contacts: UserContactDto[]): AddGroupMembersRequest => ({
    members: contacts.map(c => ({
        principalId: c.contactAppUserId!,
        principalKind: 'USER',
        groupRole: PrincipalGroupRoleName.MEMBER,
    })),
});

export const nonOwnerMembers = (group: PrincipalGroupDto) =>
    group.members.filter(m => m.groupRole !== 'OWNER');

export const memberDisplayName = (member: PrincipalGroupMemberDto, fallback: string) =>
    [member.user?.person?.firstName, member.user?.person?.lastName].filter(Boolean).join(' ')
    || member.user?.email
    || fallback;
