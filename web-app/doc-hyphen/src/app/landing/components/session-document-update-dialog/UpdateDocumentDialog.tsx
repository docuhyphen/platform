import {
    DocumentDetailedDto,
    DocumentType,
    ImageType,
    UpdateShareSessionDocumentRequest,
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
import {updateSharingSessionDocument} from "../../../../services/sharingSessionApi.ts";
import useToken from "../../../../context/useToken.tsx";
import {useDocumentAddDialogStyles} from "../session-document-add-dialog/DocumentAddDialogStyles.tsx";

interface UpdateDocumentDialogProps
{
    isOpen: boolean;
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUpdated: (document: DocumentDetailedDto) => void;
}

const UpdateDocumentDialog: React.FC<UpdateDocumentDialogProps> = ({
                                                                       isOpen,
                                                                       sessionId,
                                                                       sessionDocument,
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
    const styles = useDocumentAddDialogStyles();

    useEffect(() =>
    {
        if (sessionDocument)
        {
            setDocumentTitle(sessionDocument.title);

            if (sessionDocument.restrictedType && sessionDocument.restrictedType !== "null")
            {
                setSelectedRestrictionType(sessionDocument.restrictedType);
                setIsRestrictionEnabled(true);
            }
            else
            {
                setSelectedRestrictionType("PDF");
                setIsRestrictionEnabled(false);
            }
        }
    }, [sessionDocument]);

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
            const updatedDocument: UpdateShareSessionDocumentRequest = {
                title: documentTitle,
                restrictedType: isRestrictionEnabled ? selectedRestrictionType : undefined,
                restrictType: isRestrictionEnabled,
            };

            const result = await updateSharingSessionDocument(
                sessionId,
                sessionDocument.id,
                updatedDocument,
                token
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
                    <DialogTitle>Update session document</DialogTitle>
                    <DialogContent className={styles.documentAddDialogContainer}>
                        <Field className={styles.documentTitleField} label="New document name">
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
                                    label="Restrict type"
                                    checked={isRestrictionEnabled}
                                    onChange={(e) => setIsRestrictionEnabled(e.target.checked)}
                                />
                            </Field>
                            <Dropdown
                                disabled={!isRestrictionEnabled}
                                value={selectedRestrictionType}
                                placeholder="Select document type to restrict"
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
                        <Button
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape="circular"
                            onClick={handleUpdateDocument}
                        >
                            {updatingDocument && <Spinner size="extra-small"/>}
                            Update
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default UpdateDocumentDialog;
