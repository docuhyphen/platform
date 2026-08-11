import type {AppUserPublicDto, OrganizationBasicDto} from "../../../models/models.tsx";
import type {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import type {ExternalIdentityResolution} from "../../../../services/organizationTrust.ts";
import type {ShareConstraints} from "../../../../services/types/dtos.ts";
import type {ExchangeShareRoleName} from "../../../../services/types/roles.ts";
import {ExchangeInitiationRecipientMode} from "./exchangeInitiationRecipientMode.ts";
import type {ExchangeNewMainRecipient} from "./new-recipient/NewRecipient.tsx";

export interface ExchangeRecipientsTabProps
{
    recipientMode: ExchangeInitiationRecipientMode;
    setRecipientMode: (mode: ExchangeInitiationRecipientMode) => void;
    recipientOrg: OrganizationBasicDto | undefined;
    setRecipientOrg: (org: OrganizationBasicDto | undefined) => void;
    recipientOrgUser: AppUserPublicDto | undefined;
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    setRecipientResolution: (resolution?: ExternalIdentityResolution) => void;
    internalParticipants: AppUserPublicDto[];
    setInternalParticipants: (appUser: AppUserPublicDto[]) => void;
    newRecipient: ExchangeNewMainRecipient | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient | undefined) => void;
    isRequestingDocuments: boolean | null | undefined;
    recipientRole: ExchangeShareRoleName | undefined;
    setRecipientRole: (role: ExchangeShareRoleName | undefined) => void;
    recipientConstraints: ShareConstraints;
    setRecipientConstraints: (constraints: ShareConstraints) => void;
    allowAdditionalParticipants: boolean;
    allowAdvancedAccessControls: boolean;
}
