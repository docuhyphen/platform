import {AppUserPublicDto, ExchangeRecipientSelection, OrganizationBasicDto} from "../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../services/organizationApi.ts";
import {ExchangeInitiationRecipientMode} from "./components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";
import {ExchangeNewMainRecipient} from "./components/exchange-initiation-recipients-tab/new-recipient/NewRecipient.tsx";

interface BuildRecipientSelectionInput
{
    mode: ExchangeInitiationRecipientMode;
    organization?: OrganizationBasicDto;
    appUser?: AppUserPublicDto;
    group?: OrganizationGroupBasicDto;
    externalRecipient?: ExchangeNewMainRecipient;
    resolutionId?: string;
}

export const buildRecipientSelection = (input: BuildRecipientSelectionInput): ExchangeRecipientSelection =>
{
    switch (input.mode)
    {
        case ExchangeInitiationRecipientMode.TRUSTED_ORG:
            if (!input.organization?.id)
            {
                throw new Error("A Trusted Organization is required");
            }
            if (input.resolutionId)
            {
                return {type: "TRUSTED_PERSON", resolutionId: input.resolutionId};
            }
            if (input.group?.id)
            {
                return {
                    type: "TRUSTED_GROUP",
                    organizationId: input.organization.id,
                    groupId: input.group.id,
                };
            }
            throw new Error("A verified member or published group from the Trusted Organization is required");
        case ExchangeInitiationRecipientMode.MY_GROUPS:
            if (!input.group?.id)
            {
                throw new Error("A personal group is required");
            }
            return {type: "PERSONAL_GROUP", groupId: input.group.id};
        case ExchangeInitiationRecipientMode.MY_ORG:
            if (input.group?.id)
            {
                return {type: "INTERNAL_GROUP", groupId: input.group.id};
            }
            if (input.appUser?.id)
            {
                return {type: "REGISTERED_USER", appUserId: input.appUser.id};
            }
            throw new Error("An organization recipient is required");
        case ExchangeInitiationRecipientMode.PEOPLE:
        case ExchangeInitiationRecipientMode.EMAIL:
            if (input.appUser?.id)
            {
                return {type: "REGISTERED_USER", appUserId: input.appUser.id};
            }
            if (input.externalRecipient?.email && input.externalRecipient.firstName && input.externalRecipient.lastName)
            {
                return {
                    type: "EXTERNAL_EMAIL",
                    email: input.externalRecipient.email,
                    firstName: input.externalRecipient.firstName,
                    lastName: input.externalRecipient.lastName,
                };
            }
            throw new Error("A recipient is required");
    }
};
