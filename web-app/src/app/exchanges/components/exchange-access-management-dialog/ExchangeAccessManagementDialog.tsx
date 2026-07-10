import {ExchangeDetailedDto, ExchangeStatus, UpdateExchangeRequest} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Badge,
    Button,
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
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
    fetchSignedInUserAppUserExchange,
    requestExchangeRecipientOtp,
    updateExchange,
} from "../../../../services/exchangeApi.ts";
import {handleCheckboxChange} from "../../../exchange-initiation/formHandlers.tsx";
import {useAccessManagementDialogStyles} from "./ExchangeAccessManagementDialogStyles.tsx";
import {InfoIcon, RegenerateOTPIcon} from "../../../components/IconBundles.tsx";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import ExchangeAccessPanel from "./ExchangeAccessPanel.tsx";
import AddPersonPanel from "./AddPersonPanel.tsx";
import {useHelpSidebar} from "../../../../context/HelpSidebarContext.tsx";
import DownloadFormatRestriction from "../../../components/share-constraints/DownloadFormatRestriction.tsx";

interface ExchangeAccessManagementDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto | null;
    onExchangeAccessManagementUpdated: (exchange: ExchangeDetailedDto) => void;
}

const ExchangeAccessManagementDialog: React.FC<ExchangeAccessManagementDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeAccessManagementUpdated
    }) =>
{
    const resendCooldownSeconds = 30;
    const tabIds = {
        people: "people",
        access: "access",
        settings: "settings",
    } as const;
    type ExchangeParticipantView = {
        id: string;
        participantType?: string;
        addedDate?: string;
        appUserEmail?: string;
        appUserFirstName?: string;
        appUserLastName?: string;
        organizationGroupName?: string;
    };

    const [updatingExchange, setUpdatingExchange] = React.useState(false);
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
    const [allowedDownloadFormats, setAllowedDownloadFormats] = useState<string[] | undefined>(undefined);
    const [noAuthAccessValidityDays, setNoAuthAccessValidityDays] = useState<string>('7');
    const [selectedTab, setSelectedTab] = useState<TabValue>(tabIds.people);
    const [accessView, setAccessView] = useState<'list' | 'add-person'>('list');
    const {openHelpArticle} = useHelpSidebar();
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    const styles = useAccessManagementDialogStyles();

    useEffect(() =>
    {
        if (exchange)
        {
            setRequireRecipientSignIn(exchange.requestRecipientSignIn);
            setAllowDocumentAddition(exchange.allowDocumentAddition);
            setAllowDocumentDeletion(exchange.allowDocumentDeletion);
            setAllowDocumentDownload(exchange.allowDocumentDownload);
            setAllowDocumentUpdate(exchange.allowDocumentUpdate);
            setAllowDocumentUpload(exchange.allowDocumentUpload);
            setAllowedDownloadFormats(exchange.allowedDownloadFormats);
            setNoAuthAccessValidityDays(String(exchange.noAuthAccessValidityDays ?? 7));
            setAccessCodeError('');
            setAccessCodeStatus('');
            setResendCooldownRemaining(0);
            setSelectedTab(tabIds.people);
            setAccessView('list');
            setDialogErrorMessage(null);
        }
    }, [exchange]);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedTab(data.value);
        setAccessView('list');
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
        : 'Recipient can use one-time email access code';

    const onSendAccessCode = async () =>
    {
        if (!exchange)
        {
            setAccessCodeError('No exchange selected. Close and reopen Manage access.');
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

        if (exchange.requestRecipientSignIn)
        {
            setAccessCodeError('Save your access changes first, then send the access code.');
            return;
        }

        setSendingAccessCode(true);
        try
        {
            await requestExchangeRecipientOtp(exchange.id);
            setAccessCodeStatus('Access code sent to recipient email using.');
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
        if (!exchange)
        {
            return;
        }

        setUpdatingExchange(true)

        try
        {
            const parsedNoAuthValidityDays = Number(noAuthAccessValidityDays);
            if (!Number.isInteger(parsedNoAuthValidityDays) || parsedNoAuthValidityDays < 1 || parsedNoAuthValidityDays > 30)
            {
                setAccessCodeError('No-auth access validity must be a whole number between 1 and 30 days.');
                return;
            }

            const request: UpdateExchangeRequest = {
                requireRecipientSignIn,
                allowDocumentAddition,
                allowDocumentDeletion,
                allowDocumentDownload,
                allowDocumentUpdate,
                allowDocumentUpload,
                allowedDownloadFormats: allowDocumentDownload ? (allowedDownloadFormats ?? []) : [],
                noAuthAccessValidityDays: parsedNoAuthValidityDays,
            }
            await updateExchange(exchange.id, request);
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id);
            onExchangeAccessManagementUpdated(updatedExchange as ExchangeDetailedDto);
        }
        catch (error)
        {
            setDialogErrorMessage("Error updating access settings");
            console.error("Error updating access settings:", error);
        }
        finally
        {
            setUpdatingExchange(false);
        }
    }

    if (!exchange)
    {
        return (
            <Dialog modalType="alert" open={isOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Manage access</DialogTitle>
                        <DialogContent>
                            <MessageBar intent="warning">
                                <MessageBarBody>No exchange is selected yet.</MessageBarBody>
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

    const exchangeParticipants: ExchangeParticipantView[] = ((exchange?.participants ?? []) as ExchangeParticipantView[]);
    const peopleCount = 2 + exchangeParticipants.length;

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>
                        <div className={styles.titleRow}>
                            <span>Manage access</span>
                            <Tooltip content="Learn how access works" relationship="label">
                                <Button
                                    id={"access-mgmt-help-btn"}
                                    icon={<InfoIcon/>}
                                    appearance="subtle"
                                    shape="circular"
                                    size="medium"
                                    onClick={() => openHelpArticle('manage-access')}
                                    aria-label="How access works"
                                />
                            </Tooltip>
                        </div>
                    </DialogTitle>
                    <DialogContent>
                        <>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <TabList selectedValue={selectedTab} onTabSelect={onTabSelect} className={styles.tabList}>
                            <Tab value={tabIds.people}>Summary</Tab>
                            <Tab value={tabIds.access}>Access &amp; permissions</Tab>
                            <Tab value={tabIds.settings}>Exchange settings</Tab>
                        </TabList>

                        <div className={styles.tabPanel}>
                            {selectedTab === tabIds.people && (
                                <section className={styles.peopleSection}>
                                    <Divider alignContent="start">People in this exchange</Divider>

                                    <div className={styles.personCard}>
                                        <div className={styles.personDetails}>
                                            {(() => {
                                                const hasName = !!(exchange.initiator?.person?.firstName || exchange.initiator?.person?.lastName);
                                                return <>
                                                    <Text weight="semibold">
                                                        {hasName
                                                            ? formatName(exchange.initiator?.person?.firstName, exchange.initiator?.person?.lastName)
                                                            : (exchange.initiator?.email || 'Unknown')}
                                                    </Text>
                                                    <Text size={200} className={styles.personEmail}>
                                                        {hasName ? (exchange.initiator?.email || 'No email') : 'Requester'}
                                                    </Text>
                                                </>;
                                            })()}
                                        </div>
                                        <Badge appearance="outline" color="informative">Requester</Badge>
                                    </div>

                                    <div className={styles.personCard}>
                                        <div className={styles.personDetails}>
                                            <Text weight="semibold">
                                                {exchange.recipientGroupName
                                                    ? `${exchange.recipientGroupName} (Group)`
                                                    : (() => {
                                                        const hasName = !!(exchange.recipient?.person?.firstName || exchange.recipient?.person?.lastName);
                                                        return hasName
                                                            ? formatName(exchange.recipient?.person?.firstName, exchange.recipient?.person?.lastName)
                                                            : (exchange.recipient?.email || 'Unknown recipient');
                                                      })()}
                                            </Text>
                                            <Text size={200} className={styles.personEmail}>
                                                {exchange.recipientGroupName
                                                    ? 'Group recipient'
                                                    : (() => {
                                                        const hasName = !!(exchange.recipient?.person?.firstName || exchange.recipient?.person?.lastName);
                                                        return hasName ? (exchange.recipient?.email || 'No email') : 'External recipient';
                                                      })()}
                                            </Text>
                                        </div>
                                        <Badge appearance="outline" color="brand">Primary recipient</Badge>
                                    </div>

                                    {exchangeParticipants.map((participant) => (
                                        <div key={participant.id} className={styles.personCard}>
                                            <div className={styles.personDetails}>
                                                <Text weight="semibold">
                                                    {participant.organizationGroupName
                                                        || formatName(participant.appUserFirstName, participant.appUserLastName, participant.appUserEmail)}
                                                </Text>
                                                <Text size={200} className={styles.personEmail}>{participant.appUserEmail || participant.organizationGroupName || 'Exchange participant'}</Text>
                                            </div>
                                            <Tooltip content={participant.addedDate ? new Date(participant.addedDate).toLocaleString() : 'Added date unavailable'} relationship="label">
                                                <Badge appearance="outline" color="subtle">{participantLabel(participant.participantType)}</Badge>
                                            </Tooltip>
                                        </div>
                                    ))}

                                    {!exchangeParticipants.length && (
                                        <Text size={200}>No additional participants added.</Text>
                                    )}
                                </section>
                            )}

                            {selectedTab === tabIds.access && accessView === 'add-person' && (
                                <section>
                                    <AddPersonPanel
                                        exchangeId={exchange.id}
                                        onBack={() => setAccessView('list')}
                                        onPersonAdded={() => setAccessView('list')}
                                    />
                                </section>
                            )}

                            {selectedTab === tabIds.access && accessView === 'list' && (
                                <section>
                                    <Accordion collapsible defaultOpenItems={["access-management"]}>
                                        <AccordionItem value="access-management">
                                            <AccordionHeader>Access Management</AccordionHeader>
                                            <AccordionPanel>
                                                <ExchangeAccessPanel
                                                    exchangeId={exchange.id}
                                                    onAddPerson={() => setAccessView('add-person')}
                                                />
                                            </AccordionPanel>
                                        </AccordionItem>
                                        <AccordionItem value="document-permissions">
                                            <AccordionHeader>Document permissions</AccordionHeader>
                                            <AccordionPanel>
                                                <div className={styles.switchGroup}>
                                                    <Field>
                                                        <Switch
                                                            id={"switch-allow-document-addition"}
                                                            label="Allow document additions"
                                                            checked={allowDocumentAddition}
                                                            onChange={handleCheckboxChange(setAllowDocumentAddition)}
                                                        />
                                                    </Field>
                                                    <Field>
                                                        <Switch
                                                            id={"switch-allow-document-deletion"}
                                                            label="Allow document deletions"
                                                            checked={allowDocumentDeletion}
                                                            onChange={handleCheckboxChange(setAllowDocumentDeletion)}
                                                        />
                                                    </Field>
                                                    <Field>
                                                        <Switch
                                                            id={"switch-allow-document-download"}
                                                            label="Allow document download"
                                                            checked={allowDocumentDownload}
                                                            onChange={handleCheckboxChange(setAllowDocumentDownload)}
                                                        />
                                                    </Field>
                                                    {allowDocumentDownload && (
                                                        <DownloadFormatRestriction
                                                            allowedDownloadFormats={allowedDownloadFormats}
                                                            onChange={setAllowedDownloadFormats}
                                                        />
                                                    )}
                                                    <Field>
                                                        <Switch
                                                            id={"switch-allow-document-update"}
                                                            label="Allow document update"
                                                            checked={allowDocumentUpdate}
                                                            onChange={handleCheckboxChange(setAllowDocumentUpdate)}
                                                        />
                                                    </Field>
                                                    <Field>
                                                        <Switch
                                                            id={"switch-allow-document-upload"}
                                                            label="Allow document upload"
                                                            checked={allowDocumentUpload}
                                                            onChange={handleCheckboxChange(setAllowDocumentUpload)}
                                                        />
                                                    </Field>
                                                </div>
                                            </AccordionPanel>
                                        </AccordionItem>
                                    </Accordion>
                                </section>
                            )}

                            {selectedTab === tabIds.settings && (
                                <section className={styles.switchGroup}>
                                    <Divider alignContent="start">Exchange options</Divider>
                                    <div className={styles.requireSignInField}>
                                        <Field>
                                            <Switch
                                                id={"switch-require-recipient-sign-in"}
                                                label="Require recipient sign in"
                                                checked={requireRecipientSignIn}
                                                onChange={handleCheckboxChange(setRequireRecipientSignIn)}
                                            />
                                        </Field>
                                        <Button
                                            id={"access-mgmt-send-access-code-btn"}
                                            icon={<RegenerateOTPIcon/>}
                                            className={globalStyles.buttonWithLoading}
                                            appearance={"transparent"}
                                            shape={"circular"}
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
                                                id={"input-noauth-access-validity-days"}
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
                        </div>
                        </>
                    </DialogContent>
                    <DialogActions>
                        <>
                            <Button
                                id={"access-mgmt-save-btn"}
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onUpdate}>
                                {updatingExchange && <Spinner size={"tiny"}/>}
                                Save changes
                            </Button>
                            <Button
                                id={"access-mgmt-cancel-btn"}
                                appearance="secondary"
                                shape={"circular"}
                                disabled={updatingExchange}
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

export default ExchangeAccessManagementDialog;
