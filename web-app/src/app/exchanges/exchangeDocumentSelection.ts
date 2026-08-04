import {DocumentDetailedDto} from "../models/models.tsx";

export const resolvePreviewDocumentSelection = (
    documents: DocumentDetailedDto[],
    currentSelectionId: string | undefined,
    selectDefault: boolean
): DocumentDetailedDto | undefined =>
{
    if (documents.length === 0)
    {
        return undefined;
    }

    if (currentSelectionId)
    {
        const currentSelection = documents.find(document => document.id === currentSelectionId);
        if (currentSelection)
        {
            return currentSelection;
        }
    }

    return selectDefault ? documents[0] : undefined;
};
