import {
    DocumentDetailedDto,
    DocumentType,
    ImageType,
    UpdateShareSessionDocumentRequest
} from "../../../models/models.tsx";
import React, {useEffect} from "react";
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
    Switch
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

const UpdateDocumentDialog: React.FC<UpdateDocumentDialogProps> = (
    {
        isOpen,
        onDocumentUpdated,
        onDismiss,
        sessionId,
        sessionDocument
    }) =>
{
    const token = useToken();
    const [documentTitle, setDocumentTitle] = React.useState<string>('');
    const [restrictType, setRestrictType] = React.useState<boolean>(false);
    const [updatingDocument, setUpdatingDocument] = React.useState<boolean>(false);
    const globalStyles = useGlobalStyles();
    const styles = useDocumentAddDialogStyles();
    const [restrictedType, setRestrictedType] = React.useState<DocumentType | ImageType | string>("PDF");

    useEffect(() =>
    {
        console.log("selectedUpdateSessionDocument", sessionDocument);

        if (sessionDocument)
        {
            setDocumentTitle(sessionDocument.title);

            if(sessionDocument.restrictedType != 'null')
            {
                setRestrictedType(sessionDocument.restrictedType);
                setRestrictType(true);
            }
            else {

                setRestrictedType("PDF"); // Ensures it's always controlled
            }
        }

    }, [sessionDocument]);

    const resetState = () =>
    {
        setDocumentTitle('');
        setRestrictType(false);
        setRestrictedType("PDF");
    }

    const onDismissDialog = () =>
    {
        resetState();
        onDismiss();
    }

    const onUpdateDocument = async () =>
    {
        if (updatingDocument)
        {
            return;
        }

        setUpdatingDocument(true);

        try
        {
            const updatedDocument: UpdateShareSessionDocumentRequest = {
                title: documentTitle,
                restrictedType: restrictType ? restrictedType : undefined,
                restrictType: restrictType
            };

            const result = await updateSharingSessionDocument(sessionId, sessionDocument.id, updatedDocument, token);

            onDocumentUpdated(result);
            resetState();
            onDismiss();
        }
        catch (error: any)
        {
            alert("Error updating document");
            console.error(error);
        }
        finally
        {
            setUpdatingDocument(false);
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
                    <DialogTitle>Update session document</DialogTitle>
                    <DialogContent className={styles.documentAddDialogContainer}>
                        <Field className={styles.documentTitleField}
                               label={"New document name"}>
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
                                    checked={restrictType}
                                    onChange={(ev) => setRestrictType(ev.target.checked)}
                                />
                            </Field>
                            <Dropdown
                                disabled={!restrictType}
                                value={restrictedType || DocumentType.PDF}
                                placeholder="Select document type to restrict"
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
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    onClick={onDismissDialog}
                                    shape={"circular"}
                                    disabled={updatingDocument}>
                                Close
                            </Button>
                        </DialogTrigger>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onUpdateDocument}>
                            {updatingDocument && <Spinner size={"extra-small"}/>}
                            Update
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}

export default UpdateDocumentDialog;