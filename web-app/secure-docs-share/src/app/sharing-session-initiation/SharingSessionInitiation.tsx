import React, {ChangeEvent, useState} from 'react';
import {AddRegular, CheckmarkCircleRegular, DeleteRegular} from "@fluentui/react-icons";
import './SharingSessionInitiation.css';
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Button,
    Card,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    DialogTriggerChildProps,
    Dropdown,
    Field, InfoLabel,
    Input,
    InputOnChangeData, LabelProps,
    Menu,
    MenuButton,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Option,
    OptionGroup,
    OptionOnSelectData,
    SearchBox,
    SelectionEvents,
    Switch,
    Textarea,
    Toast,
    Toaster,
    ToastTitle,
    useId,
    useToastController,
} from "@fluentui/react-components";
import useToken from "../../context/useToken.tsx";
import {useLocation, useNavigate} from "react-router-dom";
import {
    DocumentType,
    ImageType,
    SharingSessionInitiationRequest,
    SharingSessionRequestDocument
} from "../models/models.tsx";
import {initiateSharingSession} from "../../services/api.ts";

const SharingSessionInitiation: React.FC = () =>
{
    const token = useToken();
    const navigate = useNavigate();
    const location = useLocation();

    const [isInitiating, setIsInitiating] = useState(false);
    const [sessionName, setSessionName] = useState<string>('');
    const [description, setDescription] = useState<string>('');
    const [initialShareMessage, setInitialShareMessage] = useState<string>('');
    const [requireSignIn, setRequireSignIn] = useState<boolean>(false);
    const [allowDocumentAdditions, setAllowDocumentAdditions] = useState<boolean>(false);
    const [allowDocumentDeletions, setAllowDocumentDeletions] = useState<boolean>(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState<boolean>(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState<boolean>(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState<boolean>(false);
    const [documents, setDocuments] = useState<SharingSessionRequestDocument[]>([]);
    const [recipientEmail, setRecipientEmail] = useState<string>('');

    const queryParams = new URLSearchParams(location.search);
    const request = queryParams.get('request');

    const label: string
        = request === 'true' ? 'Search email to request documents from' : 'Search email to send documents to';

    const toasterId = useId("toaster");
    const {dispatchToast} = useToastController(toasterId);

    const showFormWarningToast = (message: string) =>
    {
        dispatchToast(
            <Toast>
                <ToastTitle> {message}</ToastTitle>
            </Toast>, {intent: 'warning'},
        );
    }

    const onInitiateSession = async () =>
    {

        //ToDo: add the recipient email to the sharingSession object
        //ToDo: add validation for the recipient email
        setRecipientEmail('test-reciever-email2@email.com');

        //ToDo: when there are no documents added, show an error message dialog
        try
        {
            const sharingSession: SharingSessionInitiationRequest = {

                sessionName,
                description,
                recipientEmail,
                initialShareMessage,
                sessionDocuments: documents,
                requestRecipientSignIn: requireSignIn,
                allowDocumentAddition: allowDocumentAdditions,
                allowDocumentDeletion: allowDocumentDeletions,
                allowDocumentDownload: allowDocumentDownload,
                allowDocumentUpdate: allowDocumentUpdate,
                allowDocumentUpload: allowDocumentUpload
            };

            if (!sessionName && !sessionName.length)
            {
                showFormWarningToast('Session name is required');
                return;
            }

            let oneDocumentInvalid = documents.some((document) => !document.title);

            if (oneDocumentInvalid)
            {
                showFormWarningToast('All documents must have names');
                return;
            }

            oneDocumentInvalid = documents.some((document) =>
            {
                return document.restrictType && !document.restrictedType;
            });

            if (oneDocumentInvalid)
            {
                showFormWarningToast('All restricted documents must have a type');
                return;
            }

            //backend does not accept unknown fields (restrictType in this case)
            sharingSession?.sessionDocuments?.forEach((document) =>
            {
                document.restrictType = undefined;
            });


            setIsInitiating(true);
            const createdSharingSession = await initiateSharingSession(sharingSession, token);
            // setAppUserPersonCompany(createdSharingSession);
            // navigate(`/sharing-sessions/${createdSharingSession.id}`);

            navigate(`/sharing-sessions/${createdSharingSession.id}`);
        }
        catch (error)
        {
            console.error('Session initiation failed', error);
        }
        finally
        {
            setIsInitiating(false);
        }
    };

    const addNewDocument = () =>
    {
        setDocuments([...documents, {} as SharingSessionRequestDocument]);
    };

    const onSessionNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setSessionName(newValue.value || '');
    };

    const onDescriptionChange = (_e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) =>
    {
        setDescription(newValue.value || '');
    };

    const onInitialShareMessageChange = (_e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) =>
    {
        setInitialShareMessage(newValue.value || '');
    };

    const onDocumentNameChange = (index: number, newValue: string) =>
    {
        const updatedDocuments = [...documents];
        updatedDocuments[index].title = newValue;
        setDocuments(updatedDocuments);
    };

    const onDocumentTypeChange = (index: number, newType: DocumentType | ImageType) =>
    {
        const updatedDocuments = [...documents];
        updatedDocuments[index].restrictedType = newType;
        setDocuments(updatedDocuments);
    };

    const onRestrictDocumentTypeChange = (index: number, ev: ChangeEvent<HTMLInputElement>) =>
    {
        const updatedDocuments = [...documents];
        updatedDocuments[index].restrictType = ev.target.checked;

        setDocuments(updatedDocuments);
    };

    const onRequireSignInChange = (ev: ChangeEvent<HTMLInputElement>) =>
    {
        setRequireSignIn(ev.target.checked);
    };

    const onAllowDocumentAdditionsChange = (ev: ChangeEvent<HTMLInputElement>) =>
    {
        setAllowDocumentAdditions(ev.target.checked);
    }

    const onAllowDocumentDeletionsChange = (ev: ChangeEvent<HTMLInputElement>) =>
    {
        setAllowDocumentDeletions(ev.target.checked);
    }

    const onAllowDocumentDownloadChange = (ev: ChangeEvent<HTMLInputElement>) =>
    {
        setAllowDocumentDownload(ev.target.checked);
    }

    const onAllowDocumentUpdateChange = (ev: ChangeEvent<HTMLInputElement>) =>
    {
        setAllowDocumentUpdate(ev.target.checked);
    }

    const onAllowDocumentUploadChange = (ev: ChangeEvent<HTMLInputElement>) =>
    {
        setAllowDocumentUpload(ev.target.checked);
    }

    const CustomDialogTrigger = React.forwardRef<
        HTMLButtonElement,
        DialogTriggerChildProps
    >((props, ref) =>
    {
        return (
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <MenuButton
                        shape="circular"
                        appearance="primary">
                        Start Sharing Session
                    </MenuButton>
                </MenuTrigger>

                <MenuPopover>
                    <MenuList>
                        <MenuItem>
                            <Button size={"small"}
                                    ref={ref} {...props}
                                    appearance={"transparent"}> Request
                                Documents
                            </Button>
                        </MenuItem>
                        <MenuItem>
                            <Button size={"small"}
                                    ref={ref}
                                    {...props}
                                    appearance={"transparent"}>
                                Send Documents</Button></MenuItem>
                        <MenuItem>
                            <Button size={"small"}
                                    ref={ref} {...props}
                                    appearance={"transparent"}> From template
                            </Button>
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        );
    });

    const documentsCard = () =>
    {
        return <>
            {documents.map((document, index) => (
                <Card key={index} id={"shading-session-document-card"}>
                    <div>
                        <div id={"shading-session-document-card-header"}>
                            <Field className={"field"}>
                                <Input type="text"
                                       appearance={"underline"}
                                       size={"small"}
                                       value={document.title || ''}
                                       required
                                       onChange={(e) => onDocumentNameChange(index, e.target.value)}
                                       placeholder={"Document name"}
                                />
                            </Field>
                            <Button icon={<DeleteRegular/>}
                                    appearance={"subtle"}
                                    onClick={() =>
                            {
                                const updatedDocuments = documents.filter((_, docIndex) => docIndex !== index);
                                setDocuments(updatedDocuments);
                            }}/>
                        </div>
                        <div id={"shading-session-document-card-doc-type"}>
                            <Field label="">
                                <Switch
                                    label={"Restrict type"}
                                    checked={document.restrictType}
                                    onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                                />
                            </Field>
                            <Dropdown disabled={!document.restrictType}
                                      appearance={"underline"}
                                      size={"small"}
                                      placeholder={"Select document type to restrict"}
                                      onOptionSelect={(_e: SelectionEvents, data: OptionOnSelectData) =>
                                      {
                                          onDocumentTypeChange(index, data.optionValue as any);
                                      }}>
                                <OptionGroup label="Documents">
                                    {
                                        Object.values(DocumentType)
                                            .map((option) => (
                                                <Option key={option} value={option}>
                                                    {option}
                                                </Option>
                                            ))
                                    }
                                </OptionGroup>
                                <OptionGroup label="Images">

                                    {
                                        Object.values(ImageType)
                                            .map((option) => (
                                                <Option key={option} value={option}>
                                                    {option}
                                                </Option>
                                            ))
                                    }
                                </OptionGroup>
                            </Dropdown>
                        </div>
                    </div>
                </Card>
            ))}
        </>
    }

    return (
        <Dialog modalType="alert">
            <DialogTrigger disableButtonEnhancement>
                <CustomDialogTrigger/>
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Initiating Sharing Session</DialogTitle>
                    <DialogContent>
                        <Accordion defaultOpenItems="1" collapsible>
                            <AccordionItem value="1">
                                <AccordionHeader>Details</AccordionHeader>
                                <AccordionPanel>
                                    <Field label={label}>
                                        <SearchBox/>
                                    </Field>

                                    <Field label="Session Name" required>
                                        <Input type="text" value={sessionName} required onChange={onSessionNameChange}/>
                                    </Field>

                                    <Field label="Description">
                                        <Textarea onChange={onDescriptionChange}/>
                                    </Field>

                                    <Field label="Start message">
                                        <Textarea onChange={onInitialShareMessageChange}/>
                                    </Field>
                                </AccordionPanel>
                            </AccordionItem>
                            <AccordionItem value="2">
                                <AccordionHeader>Options</AccordionHeader>
                                <AccordionPanel>
                                    <Field>
                                        <Switch label="Require Sign In"
                                                onChange={(ev) => onRequireSignInChange(ev)}/>

                                    </Field>

                                    <Field>
                                        <Switch label="Allow document additions"
                                                onChange={(ev) => onAllowDocumentAdditionsChange(ev)}/>
                                    </Field>

                                    <Field>
                                        <Switch label="Allow document deletions"
                                                onChange={(ev) => onAllowDocumentDeletionsChange(ev)}/>
                                    </Field>

                                    <Field>
                                        <Switch label="Allow document Download"
                                                onChange={(ev) => onAllowDocumentDownloadChange(ev)}/>
                                    </Field>

                                    <Field>
                                        <Switch label="Allow document update"
                                                onChange={(ev) => onAllowDocumentUpdateChange(ev)}/>
                                    </Field>

                                    <Field>
                                        <Switch label="Allow document upload"
                                                onChange={(ev) => onAllowDocumentUploadChange(ev)}/>
                                    </Field>
                                </AccordionPanel>
                            </AccordionItem>
                            <AccordionItem value="3" disabled>
                                <AccordionHeader>Participants</AccordionHeader>
                                <AccordionPanel>
                                </AccordionPanel>
                            </AccordionItem>
                            <AccordionItem value="4">
                                <AccordionHeader>Documents {documents && documents.length > 0 &&
                                    <span> ({documents.length})</span>}
                                </AccordionHeader>
                                <AccordionPanel>
                                    {documentsCard()}
                                    <Button onClick={addNewDocument}
                                            icon={<AddRegular/>}
                                            appearance="subtle">
                                        Add Document
                                    </Button>

                                    <Toaster toasterId={toasterId}/>
                                </AccordionPanel>
                            </AccordionItem>
                        </Accordion>
                    </DialogContent>
                    <DialogActions>

                        <DialogTrigger>
                            <Button appearance="secondary">Cancel</Button>
                        </DialogTrigger>
                        <Button onClick={onInitiateSession}
                                disabled={isInitiating}
                                appearance={"primary"}
                                icon={<CheckmarkCircleRegular/>}>
                            Start Session
                        </Button>

                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SharingSessionInitiation;