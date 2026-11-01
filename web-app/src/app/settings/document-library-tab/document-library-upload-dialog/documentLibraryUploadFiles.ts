export const DOCUMENT_LIBRARY_ALLOWED_EXTENSIONS = ['pdf', 'docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt', 'png', 'jpg'];

export const DOCUMENT_LIBRARY_ACCEPTED_FILE_TYPES = DOCUMENT_LIBRARY_ALLOWED_EXTENSIONS.map(ext => `.${ext}`).join(',');

export const unsupportedFileTypeMessage = `Unsupported file type. Allowed: ${DOCUMENT_LIBRARY_ALLOWED_EXTENSIONS.join(', ')}`;

export const fileExtensionOf = (file: File): string =>
    file.name.split('.').pop()?.toLowerCase() ?? '';

export const isAllowedDocumentLibraryExtension = (extension: string): boolean =>
    DOCUMENT_LIBRARY_ALLOWED_EXTENSIONS.includes(extension);
