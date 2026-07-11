import {DocumentDetailedDto, DocumentType, ImageType} from "../../../models/models.tsx";
import React, {useState} from "react";
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
    MessageBar,
    MessageBarBody,
    Option,
    OptionGroup,
    Spinner,
    Switch,
    Text
} from "@fluentui/react-components";
import {useExchangeDocumentAddDialogStyles} from "./ExchangeDocumentAddDialogStyles.tsx";
import {addExchangeDocument} from "../../../../services/exchangeApi.ts";
import useToken from "../../../../context/useToken.tsx";

interface AddDocumentDialogProps
{
    isOpen: boolean;
    exchangeId: string,
    onDismiss: () => void;
    onDocumentAdded: (document: DocumentDetailedDto) => void;
}

const AddDocumentDialog: React.FC<AddDocumentDialogProps> = (
    {
        isOpen,
        onDocumentAdded,
        onDismiss,
        exchangeId
    }) =>
{
    const token = useToken()
    const [documentTitle, setDocumentTitle] = React.useState<string>('');
    const [restrictType, setRestrictType] = React.useState<boolean>(false);
    const [restrictedType, setRestrictedType] = React.useState<DocumentType | ImageType | undefined>(DocumentType.PDF);
    const [addingDocument, setAddingDocument] = React.useState<boolean>(false);
    const globalStyles = useGlobalStyles();
    const styles = useExchangeDocumentAddDialogStyles();
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    const resetState = () =>
    {
        setDocumentTitle('');
        setRestrictType(false);
        setRestrictedType(undefined);
        setDialogErrorMessage(null);
    }

    React.useEffect(() =>
    {
        if (!isOpen)
        {
            resetState();
        }
    }, [isOpen]);

    const onDismissDialog = () =>
    {
        resetState()
        onDismiss();
    }

    const onAddDocument = async () =>
    {
        if (addingDocument)
        {
            return;
        }

        setAddingDocument(true);

        try
        {
            const newDocument = { //ToDoAdd explicit type
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
    }

    const onOptionSelected = (_e, data) =>
    {
        return setRestrictedType(data.optionValue as DocumentType | ImageType);
    }

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Add exchange document</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field className={styles.documentTitleField}>
                            <Input
                                id={"input-add-document-title"}
                                type="text"
                                value={documentTitle}
                                required
                                onChange={(e) => setDocumentTitle(e.target.value)}
                                placeholder="Document name"
                            />
                        </Field>
                        <div className={styles.documentRestriction}>
                            <Field label="">
                                <Switch
                                    id={"switch-add-document-restrict-type"}
                                    label="Restrict upload type"
                                    checked={restrictType}
                                    onChange={(ev) => setRestrictType(ev.target.checked)}
                                />
                            </Field>
                            <Dropdown
                                id={"dropdown-add-document-type"}
                                disabled={!restrictType}
                                value={restrictedType}
                                placeholder="Select allowed upload type"
                                onOptionSelect={onOptionSelected}>
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
                        <Button id={"add-document-submit-btn"}
                                appearance="primary"
                                shape={"circular"}
                                className={globalStyles.buttonWithLoading}
                                onClick={onAddDocument}>
                            {addingDocument && <Spinner size={"tiny"}/>}
                            Add
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id={"add-document-close-btn"}
                                    appearance="secondary"
                                    onClick={onDismissDialog}
                                    shape={"circular"}
                                    disabled={addingDocument}>
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}

export default AddDocumentDialog;