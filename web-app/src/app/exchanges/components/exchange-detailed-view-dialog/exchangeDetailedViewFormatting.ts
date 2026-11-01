import {ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";

export type StatusChipAppearance = "filled";
export type StatusChipColor = "brand" | "danger" | "warning" | "subtle" | "success";

export interface StatusChipProps
{
    appearance: StatusChipAppearance;
    color: StatusChipColor;
}

export const normalizeExchangeStatus = (
    status?: ExchangeStatus | string,
    endDate?: string | null
): ExchangeStatus =>
{
    const normalizedStatus = typeof status === "string" ? status.trim().toUpperCase() : status;

    switch (normalizedStatus)
    {
        case ExchangeStatus.ACCEPTED_STARTED:
        case "ACCEPTED":
        case "STARTED":
        case "IN_PROGRESS":
            return ExchangeStatus.ACCEPTED_STARTED;
        case ExchangeStatus.REJECTED:
            return ExchangeStatus.REJECTED;
        case ExchangeStatus.RESCINDED:
            return ExchangeStatus.RESCINDED;
        case ExchangeStatus.ENDED:
        case "COMPLETED":
        case "CLOSED":
            return ExchangeStatus.ENDED;
        case ExchangeStatus.INITIATED:
            return ExchangeStatus.INITIATED;
        default:
            return endDate ? ExchangeStatus.ENDED : ExchangeStatus.INITIATED;
    }
};

export const getExchangeStatusText = (status: ExchangeStatus): string =>
{
    switch (status)
    {
        case ExchangeStatus.ACCEPTED_STARTED:
            return "In Progress";
        case ExchangeStatus.REJECTED:
            return "Rejected";
        case ExchangeStatus.RESCINDED:
            return "Rescinded";
        case ExchangeStatus.INITIATED:
            return "Initiated";
        case ExchangeStatus.ENDED:
            return "Ended";
        default:
            return "Unknown";
    }
};

export const getExchangeStatusChipProps = (status?: ExchangeStatus): StatusChipProps =>
{
    switch (status)
    {
        case ExchangeStatus.ACCEPTED_STARTED:
            return {appearance: "filled", color: "brand"};
        case ExchangeStatus.REJECTED:
            return {appearance: "filled", color: "danger"};
        case ExchangeStatus.RESCINDED:
            return {appearance: "filled", color: "warning"};
        case ExchangeStatus.ENDED:
            return {appearance: "filled", color: "subtle"};
        case ExchangeStatus.INITIATED:
        default:
            return {appearance: "filled", color: "success"};
    }
};

export const formatExchangeParticipant = (participant: ExchangeDetailedDto["initiator"]): string =>
{
    const name = [participant?.person?.firstName, participant?.person?.lastName]
        .filter(Boolean)
        .join(" ")
        .trim();
    const email = participant?.email;

    if (name && email) return `${name} (${email})`;
    if (name) return name;
    if (email) return email;
    return "Not provided";
};

export const formatExchangeRecipient = (exchange: ExchangeDetailedDto): string =>
{
    if (exchange.recipient) return formatExchangeParticipant(exchange.recipient);
    if (exchange.recipientGroupName) return `${exchange.recipientGroupName} (Group)`;
    return "Not provided";
};

export type ExchangeAdditionalParticipant = NonNullable<ExchangeDetailedDto["participants"]>[number];

export const formatAdditionalParticipant = (participant: ExchangeAdditionalParticipant): string =>
    participant.organizationGroupName
    ?? ([participant.appUserFirstName, participant.appUserLastName].filter(Boolean).join(" ") || participant.appUserEmail || "Unknown");

export const getBooleanLabel = (value?: boolean): string => value ? "Yes" : "No";

export const countUploadedDocuments = (exchange: ExchangeDetailedDto): number =>
    (exchange.documents || []).filter(document => !!document.uploadDate).length;

export const countRequestedDocuments = (exchange: ExchangeDetailedDto): number =>
    (exchange.documents || []).filter(document => !document.uploadDate).length;
