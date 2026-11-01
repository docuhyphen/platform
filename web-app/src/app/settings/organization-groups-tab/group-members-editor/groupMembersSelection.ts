import {AppUserPublicDto, OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import {PrincipalGroupRoleName} from "../../../../services/types/roles";
import {PersonPickerItem} from "../../../components/person-picker/personPickerTypes.ts";

export type MemberRoles = Map<string, PrincipalGroupRoleName>;

export const toPersonPickerItem = (user: AppUserPublicDto): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

export const validateGroupMembers = (selectedMembers: Set<string>, memberRoles: MemberRoles): string | null =>
{
    if (selectedMembers.size === 0)
    {
        return "Please add at least one member to the group";
    }
    const hasOwner = Array.from(selectedMembers).some(uid => memberRoles.get(uid) === PrincipalGroupRoleName.OWNER);
    if (!hasOwner)
    {
        return "The group must have at least one member with the Owner role.";
    }
    return null;
};

export const toGroupMemberRequests = (selectedMembers: Set<string>, memberRoles: MemberRoles) =>
    Array.from(selectedMembers).map(uid => ({
        appUserId: uid,
        groupRole: memberRoles.get(uid) || PrincipalGroupRoleName.MEMBER,
    }));

export const selectionFromGroup = (group: OrganizationGroupDetailedDto, fetchedUsers: AppUserPublicDto[]) =>
{
    const members = new Set<string>();
    const roles: MemberRoles = new Map();

    group.members?.forEach(member =>
    {
        const uid = member.user?.id;
        if (!uid) return;
        if (fetchedUsers.some(u => u.id === uid)) members.add(String(uid));
        roles.set(String(uid), member.groupRole || PrincipalGroupRoleName.MEMBER);
    });

    return {members, roles};
};
