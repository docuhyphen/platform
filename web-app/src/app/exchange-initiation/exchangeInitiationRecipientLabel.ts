import {ExchangeInitiationFormState} from "./exchangeInitiationTypes.ts";

export const buildRecipientLabel = (form: ExchangeInitiationFormState): string =>
{
    const {recipientResolution, recipientOrgGroup, recipientOrgUser, newRecipient} = form;
    if (recipientResolution)
    {
        return recipientResolution.displayName || recipientResolution.email;
    }
    if (recipientOrgGroup?.name)
    {
        return `Group: ${recipientOrgGroup.name}`;
    }
    if (recipientOrgUser?.person?.firstName || recipientOrgUser?.person?.lastName)
    {
        return `${recipientOrgUser.person?.firstName ?? ''} ${recipientOrgUser.person?.lastName ?? ''}`.trim();
    }
    if (recipientOrgUser?.email)
    {
        return recipientOrgUser.email;
    }
    if (newRecipient?.firstName || newRecipient?.lastName)
    {
        return `${newRecipient.firstName ?? ''} ${newRecipient.lastName ?? ''}`.trim();
    }
    return newRecipient?.email || 'Recipient';
};
