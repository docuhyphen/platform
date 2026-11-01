import {ReactNode} from "react";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {formatDateWithOrdinal} from "../../../helpers.ts";
import {ExchangeDetailRow} from "./exchange-detail-key-value-grid/ExchangeDetailKeyValueGrid.tsx";
import {countRequestedDocuments, countUploadedDocuments, getBooleanLabel} from "./exchangeDetailedViewFormatting.ts";

const ID_PREFIX = "exchange-detailed-view-dialog-text-";

const row = (labelIndex: number, label: string, value: ReactNode): ExchangeDetailRow => ({
    labelId: `${ID_PREFIX}${labelIndex}`,
    valueId: `${ID_PREFIX}${labelIndex + 1}`,
    label,
    value
});

export const buildLifecycleRows = (exchange: ExchangeDetailedDto): ExchangeDetailRow[] => [
    row(5, "Date initiated", formatDateWithOrdinal(exchange.createdDate)),
    row(7, "Last activity", formatDateWithOrdinal(exchange.lastActivity)),
    row(9, "Date closed", exchange.endDate ? formatDateWithOrdinal(exchange.endDate) : "-")
];

export const buildDocumentRows = (exchange: ExchangeDetailedDto): ExchangeDetailRow[] => [
    row(20, "Total", exchange.documents?.length || 0),
    row(22, "Uploaded", countUploadedDocuments(exchange)),
    row(24, "Requested", countRequestedDocuments(exchange))
];

export const buildSharingOptionRows = (exchange: ExchangeDetailedDto): ExchangeDetailRow[] => [
    row(27, "Require recipient sign-in", getBooleanLabel(exchange.requestRecipientSignIn)),
    row(29, "Allow document addition", getBooleanLabel(exchange.allowDocumentAddition)),
    row(31, "Allow document upload", getBooleanLabel(exchange.allowDocumentUpload)),
    row(33, "Allow document update", getBooleanLabel(exchange.allowDocumentUpdate)),
    row(35, "Allow document download", getBooleanLabel(exchange.allowDocumentDownload)),
    row(37, "Allow document deletion", getBooleanLabel(exchange.allowDocumentDeletion)),
    row(39, "Watermark", getBooleanLabel(exchange.watermark)),
    row(41, "Require MFA", getBooleanLabel(exchange.requireMfa))
];
