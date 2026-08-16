import {NotificationDto} from '../app/models/models';

const firstValue = (...values: unknown[]): string | undefined =>
    values.find((value): value is string => typeof value === 'string' && value.trim().length > 0)
        ?.trim();

export const getNotificationTarget = (notification: NotificationDto): string | null =>
{
    const data = notification.data ?? {};
    if (notification.type === "subscription_trial_request.created")
    {
        return "/platform/administration?section=trial-requests";
    }
    if (notification.type === "subscription_trial_request.approved"
        || notification.type === "subscription_trial_request.rejected")
    {
        return "/settings?tab=OrganizationBillingTab";
    }
    const subjectExchangeId = data.subjectType === 'EXCHANGE' ? data.subjectId : undefined;
    const exchangeId = firstValue(
        notification.exchangeId,
        data.exchangeId,
        data.exchange_id,
        subjectExchangeId,
    );
    if (!exchangeId)
    {
        return null;
    }

    const documentId = firstValue(
        notification.documentId,
        data.documentId,
        data.document_id,
    );
    const searchParams = new URLSearchParams({s: exchangeId});
    if (documentId)
    {
        searchParams.set('d', documentId);
    }
    return `/exchanges?${searchParams.toString()}`;
};
