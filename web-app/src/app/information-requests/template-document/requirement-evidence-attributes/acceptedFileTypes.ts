import {ChoiceOption} from "../../shared/choice-select/choiceOptions.ts";

const ACCEPTED_FILE_TYPES: Record<string, string> = {
    "application/pdf": "PDF document",
    "image/png": "PNG image",
    "image/jpeg": "JPEG image",
    "image/gif": "GIF image",
    "image/webp": "WebP image",
    "image/tiff": "TIFF image",
    "text/plain": "Plain text",
    "text/csv": "Comma-separated values",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "Word processing document (.docx)",
    "application/msword": "Word processing document (.doc)",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "Spreadsheet (.xlsx)",
    "application/vnd.ms-excel": "Spreadsheet (.xls)",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation": "Presentation (.pptx)",
    "application/zip": "ZIP archive",
};

export const acceptedFileTypeOptions: ChoiceOption<string>[] = Object.entries(ACCEPTED_FILE_TYPES)
    .map(([value, label]) => ({value, label: `${label} (${value})`}));

export const acceptedFileTypeLabel = (mediaType: string): string => ACCEPTED_FILE_TYPES[mediaType] ?? mediaType;
