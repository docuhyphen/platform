import {
    checkSignedInAppUserHasExchanges,
    fetchPendingExchangeRecipientInvitations,
} from "../../services/exchangeApi.ts";

export const hasExchangeWorkspaceContent = async (token: string | null): Promise<boolean> =>
{
    const [exchangeResult, invitationResult] = await Promise.allSettled([
        checkSignedInAppUserHasExchanges(token),
        fetchPendingExchangeRecipientInvitations(),
    ]);

    if (exchangeResult.status === "fulfilled" && exchangeResult.value)
    {
        return true;
    }
    if (invitationResult.status === "fulfilled" && invitationResult.value.length > 0)
    {
        return true;
    }
    if (exchangeResult.status === "rejected")
    {
        throw exchangeResult.reason;
    }
    if (invitationResult.status === "rejected")
    {
        throw invitationResult.reason;
    }

    return false;
};
