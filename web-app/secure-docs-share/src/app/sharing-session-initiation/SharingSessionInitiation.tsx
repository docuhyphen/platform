import React, {ChangeEvent, useState} from 'react';
import {AddRegular, CheckmarkCircleRegular, DeleteRegular} from "@fluentui/react-icons";
import './SharingSessionInitiation.css';
import {
    Button,
    Card,
    Divider,
    Dropdown,
    Field,
    Input,
    InputOnChangeData,
    Option,
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
import {useAuth} from "../../context/AuthContext.tsx";
import {DocumentType, SharingSessionInitiationRequest, SharingSessionRequestDocument} from "../models/models.tsx";
import {initiateSharingSession} from "../../services/api.ts";

const SharingSessionInitiation: React.FC = () =>
{
    const token = useToken();
    const {setAppUserPersonCompany, appUserPersonCompany} = useAuth();
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
    const [receiverEmail, setReceiverEmail] = useState<string>('');

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

        //ToDo: add the receiver email to the sharingSession object
        //ToDo: add validation for the receiver email
        setReceiverEmail('test-reciever-email2@email.com');

        //ToDo: when there are no documents added, show an error message dialog
        try
        {
            const sharingSession: SharingSessionInitiationRequest = {

                sessionName,
                description,
                receiverEmail,
                initialShareMessage,
                sessionDocuments: documents,
                requestReceiverSignIn: requireSignIn,
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

    const onDocumentTypeChange = (index: number, newType: DocumentType) =>
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

    return (
        <>
            <h1>Sharing Session Initiation</h1>

            <Field label={label}>
                <SearchBox/>
            </Field>

            <Field label="Session Name">
                <Input type="text" value={sessionName} required onChange={onSessionNameChange}/>
            </Field>

            <Field label="Description">
                <Textarea onChange={onDescriptionChange}/>
            </Field>

            <Field label="Custom message">
                <Textarea onChange={onInitialShareMessageChange}/>
            </Field>

            <Divider/>

            <Field label="Require Sign In">
                <Switch onChange={(ev) => onRequireSignInChange(ev)}/>
            </Field>

            <Field label="Allow document additions">
                <Switch onChange={(ev) => onAllowDocumentAdditionsChange(ev)}/>
            </Field>

            <Field label="Allow document deletions">
                <Switch onChange={(ev) => onAllowDocumentDeletionsChange(ev)}/>
            </Field>

            <Field label="Allow document Download">
                <Switch onChange={(ev) => onAllowDocumentDownloadChange(ev)}/>
            </Field>

            <Field label="Allow document update">
                <Switch onChange={(ev) => onAllowDocumentUpdateChange(ev)}/>
            </Field>

            <Field label="Allow document upload">
                <Switch onChange={(ev) => onAllowDocumentUploadChange(ev)}/>
            </Field>

            {documents.map((document, index) => (
                <Card key={index}>
                    <Field label="Document Name">
                        <Input type="text" value={document.title || ''} required
                               onChange={(e) => onDocumentNameChange(index, e.target.value)}/>
                    </Field>
                    <Field label="">
                        <Switch
                            label={"Restrict type"}
                            checked={document.restrictType}
                            onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                        />
                    </Field>
                    <Dropdown disabled={!document.restrictType}
                              onOptionSelect={(_e: SelectionEvents, data: OptionOnSelectData) =>
                              {
                                  onDocumentTypeChange(index, data.optionValue as DocumentType);
                              }}>
                        {
                            Object.values(DocumentType).map((option) => (
                                <Option key={option} value={option}>
                                    {option}
                                </Option>
                            ))
                        }
                    </Dropdown>
                    <Button onClick={() =>
                    {
                        const updatedDocuments = documents.filter((_, docIndex) => docIndex !== index);
                        setDocuments(updatedDocuments);
                    }} icon={<DeleteRegular/>}/>
                </Card>
            ))}

            <Button onClick={addNewDocument}
                    icon={<AddRegular/>}
                    appearance="subtle">
                Add Document
            </Button>

            <Button onClick={onInitiateSession}
                    disabled={isInitiating}
                    size="large"
                    appearance={"primary"}
                    icon={<CheckmarkCircleRegular/>}>
                Start Session
            </Button>
            <Button  onClick={() => navigate('/sharing-sessions')} size="large" appearance={"subtle"}>Cancel</Button>
            {
                //ChannelShareRegular}
            }
            <Toaster toasterId={toasterId}/>
        </>
    );
};

export default SharingSessionInitiation;