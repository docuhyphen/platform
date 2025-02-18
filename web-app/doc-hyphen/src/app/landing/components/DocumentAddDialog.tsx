import {DocumentDetailedDto, DocumentType, ImageType} from "../../models/models.tsx";
import React from "react";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger, Dropdown, Field, Input, Option, OptionGroup,
    Spinner, Switch
} from "@fluentui/react-components";

interface AddDocumentDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    onDocumentAdded: (document: DocumentDetailedDto) => void;
}

const AddDocumentDialog: React.FC<AddDocumentDialogProps> = (
    {
        isOpen,
        onDocumentAdded,
        onDismiss
    }) =>
{
    const [documentTitle, setDocumentTitle] = React.useState<string>('');
    const [restrictType, setRestrictType] = React.useState<boolean>(false);
    const [restrictedType, setRestrictedType] = React.useState<DocumentType>(false);

    const [deletingDocument, setDeletingDocument] = React.useState<boolean>(false);
    const globalStyles = useGlobalStyles();

    const onAddDocument = async () =>
    {
        if (deletingDocument)
        {
            return;
        }

        setDeletingDocument(true);

        try
        {

            onDocumentAdded({})
        }
        catch (error: any)
        {
            console.error(error);
        }
        finally
        {
            setDeletingDocument(false)
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Add session document</DialogTitle>
                    <DialogContent>
                        <Field >
                            <Input
                                type="text"
                                appearance="underline"
                                size="small"
                                value={documentTitle || ''}
                                required
                                // onChange={(e) => onDocumentNameChange(index, e.target.value)}
                                placeholder="Document name"
                            />
                        </Field>
                        <Field label="">
                            <Switch
                                label="Restrict type"
                                checked={restrictType}
                                // onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                            />
                        </Field>
                        <Dropdown
                            disabled={!restrictType}
                            appearance="underline"
                            value={restrictedType}
                            size="small"
                            placeholder="Select document type to restrict"
                            // onOptionSelect={(_e, data) => onDocumentTypeChange(index, data.optionValue as any)}
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
                    </DialogContent>
                    <DialogActions>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    onClick={() => onDismiss()}>
                                Close
                            </Button>
                        </DialogTrigger>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                onClick={onAddDocument}>
                            {deletingDocument && <Spinner size={"extra-small"}/>}
                            Delete
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default AddDocumentDialog;