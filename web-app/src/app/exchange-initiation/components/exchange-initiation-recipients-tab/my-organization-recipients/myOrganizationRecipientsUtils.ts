import {AppUserPublicDto} from "../../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../../services/organizationApi";
import {PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";

export const MAX_VISIBLE_GROUPS = 10;

export enum ShareWithMode
{
    INDIVIDUAL = "withIndividual",
    GROUP = "withOrgGroup"
}

export const toPersonPickerItem = (user: AppUserPublicDto): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

export const formatUserDisplay = (user: AppUserPublicDto | null | undefined): string =>
{
    if (!user) return "";
    const firstName = user.person?.firstName || '';
    const lastName = user.person?.lastName || '';
    return `${firstName} ${lastName} (${user.email})`;
};

export const groupMemberIdsOf = (group: OrganizationGroupBasicDto) =>
    new Set((group.members || []).map(member => member.user?.id).filter(Boolean));
