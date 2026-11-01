import {useEffect, useState} from "react";
import {OptionOnSelectData, SelectionEvents} from "@fluentui/react-components";
import {addExchangeDocument} from "../../../../services/exchangeApi.ts";
import {
    DocumentDetailedDto,
    DocumentType,
    ExchangeRequestDocumentRequest,
    ImageType
} from "../../../models/models.tsx";

export const useExchangeDocumentAddDialog = (
    isOpen: boolean,
    exchangeId: string,
    onDismiss: () => void,
    onDocumentAdded: (document: DocumentDetailedDto) => void
) =>
{
    const [documentTitle, setDocumentTitle] = useState<string>('');
    const [restrictType, setRestrictType] = useState<boolean>(false);
    const [restrictedType, setRestrictedType] = useState<DocumentType | ImageType | undefined>(DocumentType.PDF);
    const [addingDocument, setAddingDocument] = useState<boolean>(false);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    const resetState = () =>
    {
        setDocumentTitle('');
        setRestrictType(false);
        setRestrictedType(undefined);
        setDialogErrorMessage(null);
    };

    useEffect(() =>
    {
        if (!isOpen)
        {
            resetState();
        }
    }, [isOpen]);

    const onDismissDialog = () =>
    {
        resetState();
        onDismiss();
    };

    const onAddDocument = async () =>
    {
        if (addingDocument)
        {
            return;
        }

        setAddingDocument(true);

        try
        {
            const newDocument: ExchangeRequestDocumentRequest = {
                title: documentTitle,
                restrictedType: restrictType ? restrictedType : undefined,
                restrictType: restrictType
            };

            const addedDocument = await addExchangeDocument(exchangeId, newDocument);

            onDocumentAdded(addedDocument);
            resetState();
            onDismiss();
        }
        catch (error: unknown)
        {
            setDialogErrorMessage("Error adding document");
            console.error(error);
        }
        finally
        {
            setAddingDocument(false);
        }
    };

    const onOptionSelected = (_e: SelectionEvents, data: OptionOnSelectData) =>
    {
        setRestrictedType(data.optionValue as DocumentType | ImageType);
    };

    return {
        documentTitle,
        setDocumentTitle,
        restrictType,
        setRestrictType,
        restrictedType,
        addingDocument,
        dialogErrorMessage,
        onDismissDialog,
        onAddDocument,
        onOptionSelected
    };
};
