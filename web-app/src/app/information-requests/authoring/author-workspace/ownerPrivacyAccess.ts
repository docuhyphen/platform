import {Capability, InformationRequestDto, InformationRequestOwnerType} from "../../../models/models.tsx";

type OwnerFields = Pick<InformationRequestDto, "ownerType" | "ownerUserId" | "ownerOrganizationId">;

interface SessionFields
{
    userId: string;
    activeOrganizationId: string | null;
}

export const mayCorrectAsOwner = (
    request: OwnerFields,
    session: SessionFields | null,
    hasCapability: (capability: Capability) => boolean,
): boolean =>
{
    if (!session) return false;
    if (request.ownerType === InformationRequestOwnerType.USER)
    {
        return !session.activeOrganizationId && request.ownerUserId === session.userId;
    }
    return session.activeOrganizationId !== null
        && request.ownerOrganizationId === session.activeOrganizationId
        && hasCapability(Capability.INFORMATION_REQUEST_PRIVACY_MANAGE);
};
