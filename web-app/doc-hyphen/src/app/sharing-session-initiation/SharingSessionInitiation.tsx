import React, { ChangeEvent, useState } from 'react';
import {
    AddRegular,
    CheckmarkCircleRegular,
    OptionsRegular,
    PeopleCommunityAddRegular,
    DocumentBulletListMultipleRegular,
    DocumentOnePageRegular
} from "@fluentui/react-icons";
import './SharingSessionInitiation.css';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger, DialogTriggerChildProps,
    Divider,
    Field,
    Input, InputOnChangeData, Menu, MenuButton, MenuItem, MenuList, MenuPopover, MenuTrigger,
    SearchBox,
    SelectTabData,
    SelectTabEvent,
    Switch,
    Tab,
    TabList, TabValue,
    Text,
    Textarea, Toast, ToastTitle,
    useId,
    useToastController,
} from "@fluentui/react-components";
import useToken from "../../context/useToken.tsx";
import { useLocation, useNavigate } from "react-router-dom";
import {
    DocumentType,
    ImageType,
    SharingSessionInitiationRequest,
    SharingSessionRequestDocumentRequest
} from "../models/models.tsx";
import { initiateSharingSession } from "../../services/api.ts";
import DocumentCard from './DocumentCard';

const SharingSessionInitiation: React.FC = () => {
    const token = useToken();
    const navigate = useNavigate();
    const location = useLocation();

    const [choosingTemplate, setChoosingTemplate] = useState(false);
    const [isInitiating, setIsInitiating] = useState(false);
    const [sessionName, setSessionName] = useState<string>('');
    const [description, setDescription] = useState<string>('');
    const [initialShareMessage, setInitialShareMessage] = useState<string>('');
    const [requireSignIn, setRequireSignIn] = useState<boolean>(true);
    const [allowDocumentAdditions, setAllowDocumentAdditions] = useState<boolean>(false);
    const [allowDocumentDeletions, setAllowDocumentDeletions] = useState<boolean>(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState<boolean>(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState<boolean>(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState<boolean>(false);
    const [documents, setDocuments] = useState<SharingSessionRequestDocumentRequest[]>([]);
    const [recipientEmail, setRecipientEmail] = useState<string>('');

    const queryParams = new URLSearchParams(location.search);
    const request = queryParams.get('request');

    const label: string = request === 'true' ? 'Enter or search email to request documents from' : 'Enter or search email to send documents to';

    const toasterId = useId("toaster");
    const { dispatchToast } = useToastController(toasterId);

    const showFormWarningToast = (message: string) => {
        dispatchToast(
            <Toast>
                <ToastTitle> {message}</ToastTitle>
            </Toast>, { intent: 'warning' },
        );
    }

    const [selectedValue, setSelectedValue] = useState<TabValue>("recipients");

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) => {
        setSelectedValue(data.value);
    };

    const onInitiateSession = async () => {
        setRecipientEmail('test-reciever-email2@email.com');

        try {
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

            if (!sessionName && !sessionName.length) {
                showFormWarningToast('Session name is required');
                return;
            }

            let oneDocumentInvalid = documents.some((document) => !document.title);

            if (oneDocumentInvalid) {
                showFormWarningToast('All documents must have names');
                return;
            }

            oneDocumentInvalid = documents.some((document) => {
                return document.restrictType && !document.restrictedType;
            });

            if (oneDocumentInvalid) {
                showFormWarningToast('All restricted documents must have a type');
                return;
            }

            sharingSession?.sessionDocuments?.forEach((document) => {
                document.restrictType = undefined;
            });

            setIsInitiating(true);
            const createdSharingSession = await initiateSharingSession(sharingSession, token);
            navigate(`/sharing-sessions/${createdSharingSession.id}`);
        } catch (error) {
            console.error('Session initiation failed', error);
        } finally {
            setIsInitiating(false);
        }
    };

    const addNewDocument = () => {
        setDocuments([...documents, {} as SharingSessionRequestDocumentRequest]);
    };

    const onSessionNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => {
        setSessionName(newValue.value || '');
    };

    const onDescriptionChange = (_e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => {
        setDescription(newValue.value || '');
    };

    const onInitialShareMessageChange = (_e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => {
        setInitialShareMessage(newValue.value || '');
    };

    const onDocumentNameChange = (index: number, newValue: string) => {
        const updatedDocuments = [...documents];
        updatedDocuments[index].title = newValue;
        setDocuments(updatedDocuments);
    };

    const onDocumentTypeChange = (index: number, newType: DocumentType | ImageType) => {
        const updatedDocuments = [...documents];
        updatedDocuments[index].restrictedType = newType;
        setDocuments(updatedDocuments);
    };

    const onRestrictDocumentTypeChange = (index: number, ev: ChangeEvent<HTMLInputElement>) => {
        const updatedDocuments = [...documents];
        updatedDocuments[index].restrictType = ev.target.checked;

        setDocuments(updatedDocuments);
    };

    const onRequireSignInChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setRequireSignIn(ev.target.checked);
    };

    const onAllowDocumentAdditionsChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentAdditions(ev.target.checked);
    }

    const onAllowDocumentDeletionsChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentDeletions(ev.target.checked);
    }

    const onAllowDocumentDownloadChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentDownload(ev.target.checked);
    }

    const onAllowDocumentUpdateChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentUpdate(ev.target.checked);
    }

    const onAllowDocumentUploadChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentUpload(ev.target.checked);
    }

    const onCancelInitiation = () => {
        setSessionName('');
        setDescription('');
        setInitialShareMessage('');
        setRequireSignIn(false);
        setAllowDocumentAdditions(false);
        setAllowDocumentDeletions(false);
        setAllowDocumentDownload(false);
        setAllowDocumentUpdate(false);
        setAllowDocumentUpload(false);
        setDocuments([]);
    }

    const CustomDialogTrigger = React.forwardRef<HTMLButtonElement, DialogTriggerChildProps>((props, ref) => {
        return (
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <MenuButton shape="circular" appearance="primary">
                        Start Sharing Session
                    </MenuButton>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem>
                            <Button size={"small"} ref={ref} {...props} appearance={"transparent"}> Request Documents </Button>
                        </MenuItem>
                        <MenuItem>
                            <Button size={"small"} ref={ref} {...props} appearance={"transparent"}> Send Documents </Button>
                        </MenuItem>
                        <MenuItem>
                            <Button size={"small"} ref={ref} {...props} appearance={"transparent"}> From template </Button>
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        );
    });

    const documentsCard = () => (
        <div id="shading-session-document-cards">
            {documents.map((document, index) => (
                <DocumentCard
                    key={index}
                    document={document}
                    index={index}
                    onDocumentNameChange={onDocumentNameChange}
                    onDocumentTypeChange={onDocumentTypeChange}
                    onRestrictDocumentTypeChange={onRestrictDocumentTypeChange}
                    onDeleteDocument={(index) => {
                        const updatedDocuments = documents.filter((_, docIndex) => docIndex !== index);
                        setDocuments(updatedDocuments);
                    }}
                />
            ))}
            <div>
                <Button onClick={addNewDocument} icon={<AddRegular />} appearance="subtle">
                    Add Document
                </Button>
            </div>
        </div>
    );

    return (
        <Dialog modalType="alert">
            <DialogTrigger disableButtonEnhancement>
                <CustomDialogTrigger />
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle id="dialog-title">
                        <div id="dialog-title-1">
                            <Text size={500}> Initiating Sharing Session </Text>
                            {!choosingTemplate &&
                                <Button appearance={"outline"} size={"small"} onClick={() => setChoosingTemplate(true)}>
                                    Choose Template
                                </Button>
                            }
                            {choosingTemplate &&
                                <Button appearance={"primary"} size={"small"} onClick={() => setChoosingTemplate(false)}>
                                    Cancel template selection
                                </Button>
                            }
                        </div>
                        {choosingTemplate &&
                            <div>
                                Choosing Template
                            </div>
                        }
                        {!choosingTemplate &&
                            <TabList selectedValue={selectedValue} onTabSelect={onTabSelect}>
                                <Tab id="recipeints" icon={<PeopleCommunityAddRegular />} value="recipients">
                                    Recipients & Participants
                                </Tab>
                                <Tab id="Details" icon={<DocumentOnePageRegular />} value="details">
                                    Details
                                </Tab>
                                <Tab id="Documents" icon={<DocumentBulletListMultipleRegular />} value="documents">
                                    Documents
                                </Tab>
                                <Tab id="Options" icon={<OptionsRegular />} value="options">
                                    Options
                                </Tab>
                            </TabList>
                        }
                    </DialogTitle>
                    <DialogContent>
                        {choosingTemplate &&
                            <div>
                                Choosing Template
                            </div>
                        }
                        {!choosingTemplate &&
                            <div id="sharing-session-initiation-taps">
                                {selectedValue === "recipients" && <div>
                                    <Field label={label}>
                                        <SearchBox />
                                    </Field>
                                </div>}
                                {selectedValue === "details" &&
                                    <div id="session-details-tap">
                                        <Field label="Session Name" required>
                                            <Input type="text" value={sessionName} required onChange={onSessionNameChange} placeholder={"Required"} />
                                        </Field>
                                        <Field label="Description">
                                            <Textarea onChange={onDescriptionChange} value={description} placeholder={"Optional"} />
                                        </Field>
                                        <Field label="Start message">
                                            <Textarea onChange={onInitialShareMessageChange} value={initialShareMessage} placeholder={"optional"} />
                                        </Field>
                                    </div>
                                }
                                {selectedValue === "documents" &&
                                    <div>
                                        {documentsCard()}
                                    </div>
                                }
                                {selectedValue === "options" &&
                                    <div id="sharing-options-tap-content">
                                        <Divider alignContent="start">Session options</Divider>
                                        <Field>
                                            <Switch label="Require recipient sign in" onChange={onRequireSignInChange} />
                                        </Field>
                                        <Divider alignContent="start">Document options</Divider>
                                        <Field>
                                            <Switch label="Allow document additions" onChange={onAllowDocumentAdditionsChange} />
                                        </Field>
                                        <Field>
                                            <Switch label="Allow document deletions" onChange={onAllowDocumentDeletionsChange} />
                                        </Field>
                                        <Field>
                                            <Switch label="Allow document Download" onChange={onAllowDocumentDownloadChange} />
                                        </Field>
                                        <Field>
                                            <Switch label="Allow document update" onChange={onAllowDocumentUpdateChange} />
                                        </Field>
                                        <Field>
                                            <Switch label="Allow document upload" onChange={onAllowDocumentUploadChange} />
                                        </Field>
                                    </div>
                                }
                            </div>
                        }
                    </DialogContent>
                    <DialogActions>
                        <DialogTrigger>
                            <Button appearance="transparent" onClick={onCancelInitiation}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                        {!choosingTemplate &&
                            <Button onClick={onInitiateSession} disabled={isInitiating} appearance={"primary"} shape={"circular"} icon={<CheckmarkCircleRegular />}>
                                Start Session
                            </Button>
                        }
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SharingSessionInitiation;