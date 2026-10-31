import {useAuth} from "../../../context/AuthContext.tsx";
import {InformationRequestOwnerType} from "../../models/models.tsx";

export interface InformationRequestOwnerFields {
    ownerType: InformationRequestOwnerType;
    ownerOrganizationId?: string | null;
    ownerUserId?: string | null;
}

interface SessionOwnerFields {
    userId?: string | null;
    activeOrganizationId?: string | null;
}

export type OwnerWorkspace =
    | { kind: "current"; ownerKey: string }
    | { kind: "other" }
    | { kind: "pending" };

export const ownerKeyOf = (owner: InformationRequestOwnerFields): string | null => {
    if (owner.ownerType === InformationRequestOwnerType.ORGANIZATION) {
        return owner.ownerOrganizationId ? `organization:${owner.ownerOrganizationId}` : null;
    }
    return owner.ownerUserId ? `person:${owner.ownerUserId}` : null;
};

export const sessionOwnerKeyOf = (session: SessionOwnerFields | null): string | null => {
    if (!session) return null;
    if (session.activeOrganizationId) return `organization:${session.activeOrganizationId}`;
    return session.userId ? `person:${session.userId}` : null;
};

export const ownerWorkspaceOf = (
    owner: InformationRequestOwnerFields | null,
    session: SessionOwnerFields | null,
): OwnerWorkspace => {
    const ownerKey = owner ? ownerKeyOf(owner) : null;
    const sessionKey = sessionOwnerKeyOf(session);
    if (!ownerKey || !sessionKey) return {kind: "pending"};
    return ownerKey === sessionKey ? {kind: "current", ownerKey} : {kind: "other"};
};

export const workspaceScopeOf = (workspace: OwnerWorkspace): string =>
    workspace.kind === "current" ? workspace.ownerKey : workspace.kind;

export const useOwnerWorkspace = (owner: InformationRequestOwnerFields | null): OwnerWorkspace => {
    const {currentSession} = useAuth();
    return ownerWorkspaceOf(owner, currentSession);
};
