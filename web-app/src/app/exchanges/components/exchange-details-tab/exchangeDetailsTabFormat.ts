import {ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";

export const STATUS_LABELS: Record<string, string> = {
    INITIATED: "Pending",
    ACCEPTED_STARTED: "Active",
    REJECTED: "Rejected",
    ENDED: "Ended",
    RESCINDED: "Rescinded",
};

export const getStatusAppearance = (): "filled" | "tint" | "ghost" | "outline" => "tint";

export const getStatusColor = (status?: string): "brand" | "danger" | "important" | "informative" | "severe" | "subtle" | "success" | "warning" =>
{
    switch (status)
    {
        case ExchangeStatus.ACCEPTED_STARTED:
            return "success";
        case ExchangeStatus.ENDED:
            return "subtle";
        case ExchangeStatus.REJECTED:
            return "danger";
        case ExchangeStatus.RESCINDED:
            return "warning";
        default:
            return "informative";
    }
};

export const formatPersonName = (firstName?: string, lastName?: string): string =>
{
    const parts = [firstName, lastName].filter(Boolean);
    return parts.length > 0 ? parts.join(" ") : "-";
};

export const formatInitiatorLabel = (exchangeDetails: ExchangeDetailedDto): string =>
{
    if (!exchangeDetails.initiator) return "-";
    const name = formatPersonName(exchangeDetails.initiator.person?.firstName, exchangeDetails.initiator.person?.lastName);
    return `${name} (${exchangeDetails.initiator.email})`;
};

export const formatRecipientLabel = (exchangeDetails: ExchangeDetailedDto): string =>
{
    if (exchangeDetails.recipient)
    {
        const name = formatPersonName(exchangeDetails.recipient.person?.firstName, exchangeDetails.recipient.person?.lastName);
        return name !== '-'
            ? `${name} (${exchangeDetails.recipient.email})`
            : (exchangeDetails.recipient.email || '-');
    }
    return exchangeDetails.recipientGroupName
        ? `${exchangeDetails.recipientGroupName} (Group)`
        : "-";
};
