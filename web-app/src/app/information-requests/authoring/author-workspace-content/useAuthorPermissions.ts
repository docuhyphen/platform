import {useAuth} from "../../../../context/AuthContext.tsx";
import {
    InformationRequestPartyDto,
    InformationRequestResponseWorkspaceDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {mayCorrectAsOwner} from "../author-workspace/ownerPrivacyAccess.ts";
import {namedSubjects} from "../request-outcomes-panel/promotionEligibility.ts";
import {AuthorActionPermissions, authorActionPermissions, NO_AUTHOR_ACTIONS} from "./authorActionPermissions.ts";

export interface AuthorPermissions {
    permissions: AuthorActionPermissions;
    subjects: InformationRequestPartyDto[];
    canCorrect: boolean;
}

export const useAuthorPermissions = (
    workspace: InformationRequestResponseWorkspaceDto | null,
    parties: InformationRequestPartyDto[],
): AuthorPermissions => {
    const {currentSession, hasCapability} = useAuth();
    const permissions = workspace
        ? authorActionPermissions(workspace.request.state, workspace.executionStanding, workspace.authorActions,
            workspace.request.gatesExchangeClosure)
        : NO_AUTHOR_ACTIONS;
    const subjects = namedSubjects(parties.filter(party => party.roleKey === InformationRequestShareRoleKey.SUBJECT));
    const canCorrect = workspace ? mayCorrectAsOwner(workspace.request, currentSession, hasCapability) : false;
    return {permissions, subjects, canCorrect};
};
