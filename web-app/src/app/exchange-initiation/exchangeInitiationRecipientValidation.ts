import {AppUserDetailedDto} from "../models/models.tsx";
import {ExchangeInitiationFormState} from "./exchangeInitiationTypes.ts";
import {
    ExchangeInitiationRecipientMode
} from "./components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";

const SELF_RECIPIENT_MESSAGE = 'You cannot be the recipient of your own Exchange';

const emailRecipientProblem = (form: ExchangeInitiationFormState, appUser: AppUserDetailedDto | null): string | null =>
{
    const {newRecipient} = form;
    if (!newRecipient || !newRecipient.email)
    {
        return 'A valid recipient email is required';
    }
    if (!newRecipient.firstName || !newRecipient.lastName)
    {
        return 'Recipient first and last name are required';
    }
    if (appUser?.email && newRecipient.email.trim().toLowerCase() === appUser.email.trim().toLowerCase())
    {
        return SELF_RECIPIENT_MESSAGE;
    }
    return null;
};

const isSelfRecipient = (form: ExchangeInitiationFormState, appUser: AppUserDetailedDto | null): boolean =>
    !!form.recipientOrgUser && !!appUser && form.recipientOrgUser.id === appUser.id;

export const recipientValidationProblem = (
    form: ExchangeInitiationFormState,
    appUser: AppUserDetailedDto | null,
): string | null =>
{
    switch (form.recipientMode)
    {
        case ExchangeInitiationRecipientMode.TRUSTED_ORG:
            if (!form.recipientOrg)
            {
                return 'A valid recipient organization is required';
            }
            if (form.recipientResolution && form.recipientResolution.expiresAt <= Date.now())
            {
                return 'The trusted member verification expired. Verify the member again.';
            }
            if (!form.recipientResolution && !form.recipientOrgGroup)
            {
                return 'Verify a member or select a published group from the Trusted Organization';
            }
            return null;
        case ExchangeInitiationRecipientMode.MY_ORG:
            if (!form.recipientOrgUser && !form.recipientOrgGroup)
            {
                return 'A valid recipient user or group in your organization is required';
            }
            return isSelfRecipient(form, appUser) ? SELF_RECIPIENT_MESSAGE : null;
        case ExchangeInitiationRecipientMode.MY_GROUPS:
            return form.recipientOrgGroup ? null : 'Please select one of your personal groups as the recipient';
        case ExchangeInitiationRecipientMode.PEOPLE:
            if (form.recipientOrgUser)
            {
                return isSelfRecipient(form, appUser) ? SELF_RECIPIENT_MESSAGE : null;
            }
            return emailRecipientProblem(form, appUser);
        case ExchangeInitiationRecipientMode.EMAIL:
            return emailRecipientProblem(form, appUser);
    }
    return null;
};
