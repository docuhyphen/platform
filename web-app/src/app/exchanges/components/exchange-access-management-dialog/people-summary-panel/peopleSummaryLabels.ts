import {ExchangeDetailedDto} from "../../../../models/models.tsx";

export const formatName = (firstName?: string, lastName?: string, fallback?: string): string =>
{
    const fullName = `${firstName || ""} ${lastName || ""}`.trim();
    return fullName || fallback || "Unknown user";
};

export const participantLabel = (principalKind: string, status: string): string =>
{
    if (status === "PENDING_APPROVAL") return "Invitation pending";
    if (principalKind === "PRINCIPAL_GROUP") return "Group participant";
    return "Participant";
};

export const participantKindLabel = (principalKind: string): string =>
    principalKind === "PRINCIPAL_GROUP" ? "Trusted group" : "Trusted person";

export const participantAddedLabel = (grantedAt?: string): string =>
    grantedAt ? new Date(grantedAt).toLocaleString() : "Added date unavailable";

const initiatorHasName = (exchange: ExchangeDetailedDto): boolean =>
    !!(exchange.initiator?.person?.firstName || exchange.initiator?.person?.lastName);

const recipientHasName = (exchange: ExchangeDetailedDto): boolean =>
    !!(exchange.recipient?.person?.firstName || exchange.recipient?.person?.lastName);

export const initiatorName = (exchange: ExchangeDetailedDto): string =>
    initiatorHasName(exchange)
        ? formatName(exchange.initiator?.person?.firstName, exchange.initiator?.person?.lastName)
        : exchange.initiator?.email || "Unknown";

export const initiatorSecondary = (exchange: ExchangeDetailedDto): string =>
    initiatorHasName(exchange) ? exchange.initiator?.email || "No email" : "Requester";

export const recipientName = (exchange: ExchangeDetailedDto): string =>
    exchange.recipientGroupName
        ? `${exchange.recipientGroupName} (Group)`
        : recipientHasName(exchange)
            ? formatName(exchange.recipient?.person?.firstName, exchange.recipient?.person?.lastName)
            : exchange.recipient?.email || "Unknown recipient";

export const recipientSecondary = (exchange: ExchangeDetailedDto): string =>
    exchange.recipientGroupName
        ? "Group recipient"
        : recipientHasName(exchange) ? exchange.recipient?.email || "No email" : "External recipient";
