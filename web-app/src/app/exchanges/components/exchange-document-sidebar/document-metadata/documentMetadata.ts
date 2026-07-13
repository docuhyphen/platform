import {DocumentDetailedDto} from "../../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../../helpers.ts";

type DocumentWithOptionalSize = DocumentDetailedDto & {
    fileSize?: number;
    size?: number;
    sizeBytes?: number;
    contentLength?: number;
};

const formatDate = (value?: string) => value ? formatDateTimeWithOrdinal(value) : "-";

const formatUploader = (firstName?: string, lastName?: string) =>
    [firstName, lastName]
        .map((part) => part?.trim())
        .filter((part): part is string => Boolean(part))
        .join(" ") || "-";

const formatRestrictedType = (value?: string) =>
{
    const normalized = value?.trim();
    return normalized && normalized.toLowerCase() !== "null" ? normalized : "Any supported type";
};

const formatFileSize = (document: DocumentDetailedDto) =>
{
    const sizedDocument = document as DocumentWithOptionalSize;
    const candidate = sizedDocument.fileSize ?? sizedDocument.size ??
        sizedDocument.sizeBytes ?? sizedDocument.contentLength;
    if (candidate === undefined || Number.isNaN(Number(candidate))) return "Not available";

    const bytes = Number(candidate);
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
};

export const documentMetadataRows = (document: DocumentDetailedDto) => [
    {label: "Date Added", value: formatDate(document.createdDate)},
    {label: "Latest Uploaded Date", value: formatDate(document.uploadDate)},
    {
        label: "Last Uploaded By",
        value: formatUploader(document.lastUploadedByFirstName, document.lastUploadedByLastName),
    },
    {label: "Type", value: document.type && document.type !== "null" ? document.type : "-"},
    {label: "Restricted type", value: formatRestrictedType(document.restrictedType)},
    {label: "File size", value: formatFileSize(document)},
];
