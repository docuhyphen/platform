export interface DocumentLibraryRestrictedTypeOption
{
    ordinal: number;
    value: string;
}

export interface DocumentLibraryRestrictedTypeGroup
{
    ordinal: number;
    label: string;
    options: DocumentLibraryRestrictedTypeOption[];
}

const groupedTypes: {label: string; values: string[]}[] = [
    {label: 'Documents', values: ['PDF', 'DOCX', 'DOC', 'XLSX', 'XLS', 'PPTX', 'PPT']},
    {label: 'Images', values: ['PNG', 'JPG']},
];

let nextOrdinal = 1;

export const documentLibraryRestrictedTypeGroups: DocumentLibraryRestrictedTypeGroup[] = groupedTypes.map((group, index) => ({
    ordinal: index + 1,
    label: group.label,
    options: group.values.map(value => ({value, ordinal: nextOrdinal++})),
}));
