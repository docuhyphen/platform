import {SharingSessionDetailedDto, SharingSessionStatus, UpdateSharingSessionRequest} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Divider,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    Spinner,
    Switch,
    Text,
    Tooltip,
} from "@fluentui/react-components";
import {
    fetchSignedInUserAppUserSharingSession,
    requestSessionRecipientOtp,
    updateSharingSession,
} from "../../../../services/sharingSessionApi.ts";
import {handleCheckboxChange} from "../../../sharing-session-initiation/formHandlers.tsx";
import {useAccessManagementDialogStyles} from "./SessionAccessManagementDialogStyles.tsx";
import {RegenerateOTPIcon} from "../../../components/IconBundles.tsx";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";

interface SessionAccessManagementDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto | null;
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
    const resendCooldownSeconds = 30;
    const tabIds = {
        people: "people",
        access: "access",
        documents: "documents",
    } as const;
    type SessionParticipantView = {
        id: string;
        participantType?: string;
        addedDate?: string;
        appUserEmail?: string;
        appUserFirstName?: string;
        appUserLastName?: string;
        organizationGroupName?: string;
    };

    const [updatingSession, setUpdatingSession] = React.useState(false);
    const [sendingAccessCode, setSendingAccessCode] = React.useState(false);
    const [accessCodeStatus, setAccessCodeStatus] = React.useState<string>('');
    const [accessCodeError, setAccessCodeError] = React.useState<string>('');
    const [resendCooldownRemaining, setResendCooldownRemaining] = React.useState<number>(0);
    const globalStyles = useGlobalStyles()
    const [requireRecipientSignIn, setRequireRecipientSignIn] = useState<boolean>(true);
    const [allowDocumentAddition, setAllowDocumentAddition] = useState<boolean>(false);
    const [allowDocumentDeletion, setAllowDocumentDeletion] = useState<boolean>(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState<boolean>(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState<boolean>(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState<boolean>(false);
    const [noAuthAccessValidityDays, setNoAuthAccessValidityDays] = useState<string>('7');
    const [selectedTab, setSelectedTab] = useState<TabValue>(tabIds.people);

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
            setNoAuthAccessValidityDays(String(session.noAuthAccessValidityDays ?? 7));
            setAccessCodeError('');
            setAccessCodeStatus('');
            setResendCooldownRemaining(0);
            setSelectedTab(tabIds.people);
        }
    }, [session]);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedTab(data.value);
    }

    useEffect(() =>
    {
        if (resendCooldownRemaining <= 0)
        {
            return;
        }

        const timerId = window.setInterval(() =>
        {
            setResendCooldownRemaining((prev) => Math.max(prev - 1, 0));
        }, 1000);

        return () => window.clearInterval(timerId);
    }, [resendCooldownRemaining]);

    const formatName = (firstName?: string, lastName?: string, fallback?: string): string =>
    {
        const fullName = `${firstName || ''} ${lastName || ''}`.trim();
        return fullName || fallback || 'Unknown user';
    }

    const participantLabel = (participantType?: string): string =>
    {
        if (participantType === 'GROUP') return 'Group participant';
        if (participantType === 'APP_USER') return 'User participant';
        return 'Participant';
    }

    const accessModeLabel = requireRecipientSignIn
        ? 'Recipient must sign in with account'
        : 'Recipient can use one-time email access code (NAS)';

    const onSendAccessCode = async () =>
    {
        if (!session)
        {
            setAccessCodeError('No sharing session selected. Close and reopen Manage access.');
            return;
        }

        if (sendingAccessCode || resendCooldownRemaining > 0)
        {
            return;
        }

        setAccessCodeStatus('');
        setAccessCodeError('');
        if (requireRecipientSignIn)
        {
            setAccessCodeError('Disable "Require recipient sign in" to send a no-auth access code.');
            return;
        }

        if (session.requestRecipientSignIn)
        {
            setAccessCodeError('Save your access changes first, then send the access code.');
            return;
        }

        setSendingAccessCode(true);
        try
        {
            await requestSessionRecipientOtp(session.id);
            setAccessCodeStatus('Access code sent to recipient email using the NAS OTP template.');
            setResendCooldownRemaining(resendCooldownSeconds);
        }
        catch (error: unknown)
        {
            const normalized = normalizeApiError(error, 'Could not send access code. Please try again.');
            setAccessCodeError(getOtpFriendlyMessage(normalized));
            if (normalized.retryAfterSeconds && normalized.retryAfterSeconds > 0)
            {
                setResendCooldownRemaining(Math.ceil(normalized.retryAfterSeconds));
            }
        }
        finally
        {
            setSendingAccessCode(false);
        }
    }

    const onUpdate = async () =>
    {
        if (!session)
        {
            return;
        }

        setUpdatingSession(true)

        try
        {
            const parsedNoAuthValidityDays = Number(noAuthAccessValidityDays);
            if (!Number.isInteger(parsedNoAuthValidityDays) || parsedNoAuthValidityDays < 1 || parsedNoAuthValidityDays > 30)
            {
                setAccessCodeError('No-auth access validity must be a whole number between 1 and 30 days.');
                return;
            }

            const request: UpdateSharingSessionRequest = {
                requireRecipientSignIn,
                allowDocumentAddition,
                allowDocumentDeletion,
                allowDocumentDownload,
                allowDocumentUpdate,
                allowDocumentUpload,
                noAuthAccessValidityDays: parsedNoAuthValidityDays,
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

    if (!session)
    {
        return (
            <Dialog modalType="alert" open={isOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Manage access</DialogTitle>
                        <DialogContent>
                            <MessageBar intent="warning">
                                <MessageBarBody>No sharing session is selected yet.</MessageBarBody>
                            </MessageBar>
                        </DialogContent>
                        <DialogActions>
                            <Button appearance="secondary" shape={"circular"} onClick={onDismiss}>
                                Close
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        );
    }

    const sessionParticipants: SessionParticipantView[] = ((session?.participants ?? []) as SessionParticipantView[]);
    const peopleCount = 2 + sessionParticipants.length;
    const enabledDocumentPermissionCount = [
        allowDocumentAddition,
        allowDocumentDeletion,
        allowDocumentDownload,
        allowDocumentUpdate,
        allowDocumentUpload,
    ].filter(Boolean).length;

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Manage access</DialogTitle>
                    <DialogContent>
                        <TabList selectedValue={selectedTab} onTabSelect={onTabSelect} className={styles.tabList}>
                            <Tab value={tabIds.people}>People ({peopleCount})</Tab>
                            <Tab value={tabIds.access}>Session access</Tab>
                            <Tab value={tabIds.documents}>Document permissions ({enabledDocumentPermissionCount}/5)</Tab>
                        </TabList>

                        <div className={styles.tabPanel}>
                            {selectedTab === tabIds.people && (
                                <section className={styles.peopleSection}>
                                    <Divider alignContent="start">People in this session</Divider>

                                    <div className={styles.personCard}>
                                        <div className={styles.personDetails}>
                                            <Text weight="semibold">{formatName(session.initiator?.person?.firstName, session.initiator?.person?.lastName, session.initiator?.email)}</Text>
                                            <Text size={200} className={styles.personEmail}>{session.initiator?.email || 'No email'}</Text>
                                        </div>
                                        <Badge appearance="outline" color="informative">Requester</Badge>
                                    </div>

                                    <div className={styles.personCard}>
                                        <div className={styles.personDetails}>
                                            <Text weight="semibold">{formatName(session.recipient?.person?.firstName, session.recipient?.person?.lastName, session.recipient?.email)}</Text>
                                            <Text size={200} className={styles.personEmail}>{session.recipient?.email || 'No email'}</Text>
                                        </div>
                                        <Badge appearance="outline" color="brand">Primary recipient</Badge>
                                    </div>

                                    {sessionParticipants.map((participant) => (
                                        <div key={participant.id} className={styles.personCard}>
                                            <div className={styles.personDetails}>
                                                <Text weight="semibold">
                                                    {participant.organizationGroupName
                                                        || formatName(participant.appUserFirstName, participant.appUserLastName, participant.appUserEmail)}
                                                </Text>
                                                <Text size={200} className={styles.personEmail}>{participant.appUserEmail || participant.organizationGroupName || 'Session participant'}</Text>
                                            </div>
                                            <Tooltip content={participant.addedDate ? new Date(participant.addedDate).toLocaleString() : 'Added date unavailable'} relationship="label">
                                                <Badge appearance="outline" color="subtle">{participantLabel(participant.participantType)}</Badge>
                                            </Tooltip>
                                        </div>
                                    ))}

                                    {!sessionParticipants.length && (
                                        <Text size={200}>No additional participants added.</Text>
                                    )}

                                    <Divider alignContent="start">Session summary</Divider>
                                    <div className={styles.metaGrid}>
                                        <Text size={200}>Status</Text>
                                        <Text weight="semibold">{session.status || SharingSessionStatus.INITIATED}</Text>
                                        <Text size={200}>Documents</Text>
                                        <Text weight="semibold">{session.documents?.length || 0}</Text>
                                        <Text size={200}>Last activity</Text>
                                        <Text weight="semibold">{session.lastActivity ? new Date(session.lastActivity).toLocaleString() : 'Unknown'}</Text>
                                    </div>
                                </section>
                            )}

                            {selectedTab === tabIds.access && (
                                <section className={styles.switchGroup}>
                                    <Divider alignContent="start">Session options</Divider>
                                    <div className={styles.requireSignInField}>
                                        <Field>
                                            <Switch
                                                label="Require recipient sign in"
                                                checked={requireRecipientSignIn}
                                                onChange={handleCheckboxChange(setRequireRecipientSignIn)}
                                            />
                                        </Field>
                                        <Button icon={<RegenerateOTPIcon/>}
                                                className={globalStyles.buttonWithLoading}
                                                appearance={"transparent"}
                                                disabled={requireRecipientSignIn || sendingAccessCode || resendCooldownRemaining > 0}
                                                onClick={onSendAccessCode}>
                                            {sendingAccessCode && <Spinner size={"tiny"}/>}
                                            {resendCooldownRemaining > 0
                                                ? `Resend access code (${resendCooldownRemaining}s)`
                                                : 'Send access code'}
                                        </Button>
                                    </div>
                                    <Text size={200}>{accessModeLabel}</Text>

                                    {!requireRecipientSignIn && (
                                        <Field label="No-auth access expires after (days)">
                                            <Input
                                                type="number"
                                                min={1}
                                                max={30}
                                                value={noAuthAccessValidityDays}
                                                onChange={(_, data) => setNoAuthAccessValidityDays(data.value)}
                                                contentAfter="days"
                                            />
                                        </Field>
                                    )}

                                    {accessCodeStatus &&
                                        <MessageBar intent="success">
                                            <MessageBarBody>{accessCodeStatus}</MessageBarBody>
                                        </MessageBar>
                                    }

                                    {accessCodeError &&
                                        <MessageBar intent="error">
                                            <MessageBarBody>{accessCodeError}</MessageBarBody>
                                        </MessageBar>
                                    }
                                </section>
                            )}

                            {selectedTab === tabIds.documents && (
                                <section className={styles.switchGroup}>
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
                                </section>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <>
                            <Button appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    onClick={onUpdate}>
                                {updatingSession && <Spinner size={"tiny"}/>}
                                Save changes
                            </Button>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={updatingSession}
                                    onClick={onDismiss}>
                                Cancel
                            </Button>
                        </>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default SessionAccessManagementDialog;