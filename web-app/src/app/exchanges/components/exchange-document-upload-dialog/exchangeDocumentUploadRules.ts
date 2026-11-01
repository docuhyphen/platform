import {DocumentType, ImageType} from "../../../models/models.tsx";

export const maxUploadSizeBytes = 52_428_800;
export const minUploadSizeBytes = 100;

const extensionMap: Record<DocumentType | ImageType, string> = {
    PDF: ".pdf",
    DOCX: ".docx",
    DOC: ".doc",
    XLSX: ".xlsx",
    XLS: ".xls",
    PPTX: ".pptx",
    PPT: ".ppt",
    PNG: ".png",
    JPG: ".jpg",
};

export const resolveAllowedExtensions = (restrictedType: string | undefined): string[] =>
{
    const restricted = (restrictedType || '').toUpperCase() as DocumentType;
    const allowedTypes = restricted && Object.values(DocumentType).includes(restricted)
        ? [restricted]
        : Object.values(DocumentType);
    return allowedTypes.map(type => extensionMap[type]);
};

export const resolveRestrictionLabel = (restrictedType: string | undefined): string =>
{
    if (!restrictedType)
    {
        return 'Any supported type';
    }

    const normalizedRestriction = String(restrictedType).trim();
    if (!normalizedRestriction || normalizedRestriction.toLowerCase() === 'null')
    {
        return 'Any supported type';
    }

    return normalizedRestriction;
};

export const formatFileSize = (size: number): string =>
{
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
};

export const getFileExtension = (fileName: string): string =>
{
    const i = fileName.lastIndexOf('.');
    return i >= 0 ? fileName.substring(i).toLowerCase() : '';
};

export const validatePickedFile = (pickedFile: File, allowedExtensions: string[]): string | null =>
{
    const extension = getFileExtension(pickedFile.name);
    if (!allowedExtensions.includes(extension))
    {
        return `Invalid file type. Allowed: ${allowedExtensions.join(', ')}`;
    }

    if (pickedFile.size > maxUploadSizeBytes)
    {
        return "File is too large. Maximum size allowed is 50 MB.";
    }

    if (pickedFile.size < minUploadSizeBytes)
    {
        return "File is too small. Minimum size required is 100 bytes.";
    }

    return null;
};
