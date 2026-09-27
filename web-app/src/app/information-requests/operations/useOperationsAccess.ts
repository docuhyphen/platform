import {useAuth} from "../../../context/AuthContext.tsx";
import {Capability} from "../../models/models.tsx";

export interface OperationsAccess
{
    canViewOperations: boolean;
    canReadRecords: boolean;
    canManageHolds: boolean;
    canManageRetention: boolean;
}

export const useOperationsAccess = (): OperationsAccess =>
{
    const {currentSession, hasCapability} = useAuth();
    const personal = currentSession !== null && !currentSession.activeOrganizationId;
    const permits = (capability: Capability): boolean => personal || hasCapability(capability);

    return {
        canViewOperations: permits(Capability.INFORMATION_REQUEST_OPERATIONS_READ),
        canReadRecords: permits(Capability.ORG_AUDIT_READ),
        canManageHolds: permits(Capability.AUDIT_LEGAL_HOLD_MANAGE),
        canManageRetention: permits(Capability.AUDIT_RETENTION_MANAGE),
    };
};
