import {SharingSessionDetailedDto, UpdateSharingSessionRequest} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import useToken from "../../../../context/useToken.tsx";
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
    Divider,
    Field,
    Spinner,
    Switch
} from "@fluentui/react-components";
import {fetchSignedInUserAppUserSharingSession, updateSharingSession} from "../../../../services/sharingSessionApi.ts";
import {handleCheckboxChange} from "../../../sharing-session-initiation/formHandlers.tsx";
import {useAccessManagementDialogStyles} from "./SessionAccessManagementDialogStyles.tsx";
import {RegenerateOTPIcon} from "../../../components/IconBundles.tsx";

interface SessionAccessManagementDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto;
    onSessionAccessManagementUpdated: (session: SharingSessionDetailedDto) => void;
}

const SessionAccessManagementDialog: React.FC<SessionAccessManagementDialogProps> = (
    {
        isOpen,
        onDismiss,
        session,
        onSessionAccessManagementUpdated
    }) =>
{

    const token = useToken();
    const [updatingSession, setUpdatingSession] = React.useState(false);
    const globalStyles = useGlobalStyles()
    const [requireRecipientSignIn, setRequireRecipientSignIn] = useState<boolean>(true);
    const [allowDocumentAddition, setAllowDocumentAddition] = useState<boolean>(false);
    const [allowDocumentDeletion, setAllowDocumentDeletion] = useState<boolean>(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState<boolean>(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState<boolean>(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState<boolean>(false);

    const styles = useAccessManagementDialogStyles();

    useEffect(() =>
    {

        if (session)
        {
            setRequireRecipientSignIn(session.requestRecipientSignIn);
            setAllowDocumentAddition(session.allowDocumentAddition);
            setAllowDocumentDeletion(session.allowDocumentDeletion);
            setAllowDocumentDownload(session.allowDocumentDownload);
            setAllowDocumentUpdate(session.allowDocumentUpdate);
            setAllowDocumentUpload(session.allowDocumentUpload);
        }

    }, [session]);

    const onUpdate = async () =>
    {

        setUpdatingSession(true)

        try
        {
            const request: UpdateSharingSessionRequest = {
                requireRecipientSignIn,
                allowDocumentAddition,
                allowDocumentDeletion,
                allowDocumentDownload,
                allowDocumentUpdate,
                allowDocumentUpload
            }
            await updateSharingSession(session.id, request);
            const updatedSession = await fetchSignedInUserAppUserSharingSession(session.id);
            onSessionAccessManagementUpdated(updatedSession as SharingSessionDetailedDto);
            onDismiss();
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setUpdatingSession(false);
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle> Management access</DialogTitle>
                    <DialogContent>
                        <div className={styles.switchGroup}>
                            <Divider alignContent="start">Session options</Divider>
                            <div className={styles.requireSignInField}>
                                <Field>
                                    <Switch
                                        label="Require recipient sign in"
                                        checked={requireRecipientSignIn}
                                        onChange={handleCheckboxChange(setRequireRecipientSignIn)}
                                    />
                                </Field>
                                {!requireRecipientSignIn &&
                                    <Button icon={<RegenerateOTPIcon/>}
                                            className={globalStyles.buttonWithLoading}
                                            appearance={"transparent"}>
                                        {/*<Spinner size={"tiny"}/>*/}
                                        Resend OTP
                                    </Button>
                                }
                            </div>
                            <Divider alignContent="start">Document options</Divider>
                            <Field>
                                <Switch
                                    label="Allow document additions"
                                    checked={allowDocumentAddition}
                                    onChange={handleCheckboxChange(setAllowDocumentAddition)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    label="Allow document deletions"
                                    checked={allowDocumentDeletion}
                                    onChange={handleCheckboxChange(setAllowDocumentDeletion)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    label="Allow document download"
                                    checked={allowDocumentDownload}
                                    onChange={handleCheckboxChange(setAllowDocumentDownload)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    label="Allow document update"
                                    checked={allowDocumentUpdate}
                                    onChange={handleCheckboxChange(setAllowDocumentUpdate)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    label="Allow document upload"
                                    checked={allowDocumentUpload}
                                    onChange={handleCheckboxChange(setAllowDocumentUpload)}
                                />
                            </Field>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <>
                            <Button appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    onClick={onUpdate}>
                                {updatingSession && <Spinner size={"tiny"}/>}
                                Update
                            </Button>
                            <DialogTrigger disableButtonEnhancement>
                                <Button appearance="secondary"
                                        shape={"circular"}
                                        disabled={updatingSession}
                                        onClick={onDismiss}>
                                    Cancel
                                </Button>
                            </DialogTrigger>
                        </>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default SessionAccessManagementDialog;