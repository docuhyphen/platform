import {CurrentSessionDto, SessionOrganizationOptionDto} from "../../app/models/models.tsx";

export type OrgSelectionResolution =
    | { kind: "none" }
    | { kind: "auto"; organizationId: string }
    | { kind: "picker"; organizations: SessionOrganizationOptionDto[] };

export function resolveOrgSelection(session: CurrentSessionDto, personalMode = false): OrgSelectionResolution
{
    if (personalMode)
    {
        return {kind: "none"};
    }
    const orgs = session.availableOrganizations ?? [];
    if (orgs.length === 0)
    {
        return {kind: "none"};
    }
    if (session.activeOrganizationId)
    {
        return {kind: "none"};
    }
    if (orgs.length === 1)
    {
        return {kind: "auto", organizationId: orgs[0].organizationId};
    }
    return {kind: "picker", organizations: orgs};
}
