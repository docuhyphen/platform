import {useCallback, useEffect, useState} from "react";
import {OptionOnSelectData, SelectionEvents} from "@fluentui/react-components";
import {updateExchangeDocument} from "../../../../services/exchangeApi.ts";
import {
    DocumentDetailedDto,
    DocumentType,
    ExchangeRequestDocumentRequest,
    ImageType
} from "../../../models/models.tsx";

export const useExchangeDocumentUpdateDialog = (
    exchangeId: string,
    exchangeDocument: DocumentDetailedDto,
    onDismiss: () => void,
    onDocumentUpdated: (document: DocumentDetailedDto) => void
) =>
{
    const [documentTitle, setDocumentTitle] = useState<string>("");
    const [isRestrictionEnabled, setIsRestrictionEnabled] = useState<boolean>(false);
    const [updatingDocument, setUpdatingDocument] = useState<boolean>(false);
    const [selectedRestrictionType, setSelectedRestrictionType] = useState<DocumentType | ImageType | string>("PDF");
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (exchangeDocument)
        {
            setDocumentTitle(exchangeDocument.title);

            const normalizedRestriction = exchangeDocument.restrictedType
                ? String(exchangeDocument.restrictedType).trim()
                : "";
            const hasRestriction = !!normalizedRestriction && normalizedRestriction.toLowerCase() !== "null";

            if (hasRestriction)
            {
                setSelectedRestrictionType(normalizedRestriction);
                setIsRestrictionEnabled(true);
            }
            else
            {
                setSelectedRestrictionType("PDF");
                setIsRestrictionEnabled(false);
            }
        }
    }, [exchangeDocument]);

    const resetState = useCallback(() =>
    {
        setDocumentTitle("");
        setIsRestrictionEnabled(false);
        setSelectedRestrictionType("PDF");
        setDialogErrorMessage(null);
    }, []);

    const handleDismiss = useCallback(() =>
    {
        resetState();
        onDismiss();
    }, [onDismiss, resetState]);

    const handleUpdateDocument = async () =>
    {
        if (updatingDocument) return;

        setUpdatingDocument(true);

        try
        {
            const updatedDocument: ExchangeRequestDocumentRequest = {
                title: documentTitle,
                restrictedType: isRestrictionEnabled ? selectedRestrictionType as DocumentType | ImageType : undefined,
                restrictType: isRestrictionEnabled,
            };

            const result = await updateExchangeDocument(
                exchangeId,
                exchangeDocument.id,
                updatedDocument
            );
            onDocumentUpdated(result);
            resetState();
            onDismiss();
        }
        catch (error: unknown)
        {
            console.error("Error updating document", error);
            setDialogErrorMessage("Error updating document");
        }
        finally
        {
            setUpdatingDocument(false);
        }
    };

    const handleOptionSelect = (_event: SelectionEvents, data: OptionOnSelectData) =>
    {
        setSelectedRestrictionType(data.optionValue);
    };

    return {
        documentTitle,
        setDocumentTitle,
        isRestrictionEnabled,
        setIsRestrictionEnabled,
        selectedRestrictionType,
        updatingDocument,
        dialogErrorMessage,
        handleDismiss,
        handleUpdateDocument,
        handleOptionSelect
    };
};
