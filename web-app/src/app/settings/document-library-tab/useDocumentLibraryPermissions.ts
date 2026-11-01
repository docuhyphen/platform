import {Capability, DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {useAuth} from '../../../context/AuthContext.tsx';
import {DocumentLibraryListTab} from './documentLibraryListTypes.ts';

export const useDocumentLibraryPermissions = (activeTab: DocumentLibraryListTab) =>
{
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const hasOrg = !!appUserPersonOrganization?.isActive;
    const canManageOrganization = hasOrg && hasCapability(Capability.ORG_POLICY_MANAGE);
    const canCreate = activeTab === 'PERSONAL' || (activeTab === 'ORG' && canManageOrganization);
    const canManageItem = (entry: DocumentLibraryEntrySummaryDto) =>
        entry.scope === 'PERSONAL' || (entry.scope === 'ORG' && canManageOrganization);

    return {hasOrg, canManageOrganization, canCreate, canManageItem};
};
