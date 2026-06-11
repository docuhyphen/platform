import {
    DocumentDetailedDto,
    DocumentType,
    ImageType,
    UpdateShareExchangeDocumentRequest,
} from "../../../models/models.tsx";
import React, {useCallback, useEffect, useState} from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Dropdown,
    Field,
    Input,
    Option,
    OptionGroup,
    Spinner,
    Switch,
} from "@fluentui/react-components";
import {updateExchangeDocument} from "../../../../services/exchangeApi.ts";
import useToken from "../../../../context/useToken.tsx";
import {useExchangeDocumentAddDialogStyles} from "../exchange-document-add-dialog/ExchangeDocumentAddDialogStyles.tsx";

interface UpdateDocumentDialogProps
{
    isOpen: boolean;
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUpdated: (document: DocumentDetailedDto) => void;
}

const ExchangeDocumentUpdateDialog: React.FC<UpdateDocumentDialogProps> = (
    {
        isOpen,
        exchangeId,
        exchangeDocument,
        onDismiss,
        onDocumentUpdated,
    }) =>
{
    const token = useToken();
    const [documentTitle, setDocumentTitle] = useState<string>("");
    const [isRestrictionEnabled, setIsRestrictionEnabled] = useState<boolean>(false);
    const [updatingDocument, setUpdatingDocument] = useState<boolean>(false);
    const [selectedRestrictionType, setSelectedRestrictionType] = useState<DocumentType | ImageType | string>("PDF");

    const globalStyles = useGlobalStyles();
    const styles = useExchangeDocumentAddDialogStyles();

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
            const updatedDocument: UpdateShareExchangeDocumentRequest = {
                title: documentTitle,
                restrictedType: isRestrictionEnabled ? selectedRestrictionType : undefined,
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
        catch (error: any)
        {
            console.error("Error updating document", error);
            alert("Error updating document");
        }
        finally
        {
            setUpdatingDocument(false);
        }
    };

    const handleOptionSelect = (
        _event: unknown,
        data: { optionValue: DocumentType | ImageType }
    ) =>
    {
        setSelectedRestrictionType(data.optionValue);
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Update exchange document</DialogTitle>
                    <DialogContent>
                        <Field className={styles.documentTitleField}
                               label="New document name">
                            <Input
                                type="text"
                                value={documentTitle}
                                required
                                onChange={(e) => setDocumentTitle(e.target.value)}
                            />
                        </Field>
                        <div className={styles.documentRestriction}>
                            <Field label="">
                                <Switch
                                    label="Restrict upload type"
                                    checked={isRestrictionEnabled}
                                    onChange={(e) => setIsRestrictionEnabled(e.target.checked)}
                                />
                            </Field>
                            <Dropdown
                                disabled={!isRestrictionEnabled}
                                value={selectedRestrictionType}
                                placeholder="Select allowed upload type"
                                onOptionSelect={handleOptionSelect}
                            >
                                <OptionGroup label="Documents">
                                    {Object.values(DocumentType).map((option) => (
                                        <Option key={option} value={option}>
                                            {option}
                                        </Option>
                                    ))}
                                </OptionGroup>
                                <OptionGroup label="Images">
                                    {Object.values(ImageType).map((option) => (
                                        <Option key={option} value={option}>
                                            {option}
                                        </Option>
                                    ))}
                                </OptionGroup>
                            </Dropdown>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape="circular"
                            onClick={handleUpdateDocument}>
                            {updatingDocument && <Spinner size="tiny"/>}
                            Update
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                appearance="secondary"
                                onClick={handleDismiss}
                                shape="circular"
                                disabled={updatingDocument}
                            >
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDocumentUpdateDialog;
