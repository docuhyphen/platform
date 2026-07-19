import {SelectTabData, SelectTabEvent, TabValue} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {
    fetchSignedInUserAppUserExchange,
    requestExchangeRecipientOtp,
    updateExchange,
} from "../../../../services/exchangeApi.ts";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import {ExchangeDetailedDto, UpdateExchangeRequest} from "../../../models/models.tsx";
import {AccessManagementView, accessManagementTabIds} from "./exchangeAccessManagementTypes.ts";

const RESEND_COOLDOWN_SECONDS = 30;

interface UseExchangeAccessManagementDialogProps
{
    exchange: ExchangeDetailedDto | null;
    onExchangeAccessManagementUpdated: (exchange: ExchangeDetailedDto) => void;
}

export const useExchangeAccessManagementDialog = (
    {exchange, onExchangeAccessManagementUpdated}: UseExchangeAccessManagementDialogProps,
) =>
{
    const [updatingExchange, setUpdatingExchange] = useState(false);
    const [sendingAccessCode, setSendingAccessCode] = useState(false);
    const [accessCodeStatus, setAccessCodeStatus] = useState("");
    const [accessCodeError, setAccessCodeError] = useState("");
    const [resendCooldownRemaining, setResendCooldownRemaining] = useState(0);
    const [requireRecipientSignIn, setRequireRecipientSignIn] = useState(true);
    const [allowDocumentAddition, setAllowDocumentAddition] = useState(false);
    const [allowDocumentDeletion, setAllowDocumentDeletion] = useState(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState(false);
    const [allowedDownloadFormats, setAllowedDownloadFormats] = useState<string[] | undefined>();
    const [noAuthAccessValidityDays, setNoAuthAccessValidityDays] = useState("7");
    const [selectedTab, setSelectedTab] = useState<TabValue>(accessManagementTabIds.people);
    const [accessView, setAccessView] = useState<AccessManagementView>("list");
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!exchange) return;
        setRequireRecipientSignIn(exchange.requestRecipientSignIn);
        setAllowDocumentAddition(exchange.allowDocumentAddition);
        setAllowDocumentDeletion(exchange.allowDocumentDeletion);
        setAllowDocumentDownload(exchange.allowDocumentDownload);
        setAllowDocumentUpdate(exchange.allowDocumentUpdate);
        setAllowDocumentUpload(exchange.allowDocumentUpload);
        setAllowedDownloadFormats(exchange.allowedDownloadFormats);
        setNoAuthAccessValidityDays(String(exchange.noAuthAccessValidityDays ?? 7));
        setAccessCodeError("");
        setAccessCodeStatus("");
        setResendCooldownRemaining(0);
        setSelectedTab(accessManagementTabIds.people);
        setAccessView("list");
        setDialogErrorMessage(null);
    }, [exchange]);

    useEffect(() =>
    {
        if (resendCooldownRemaining <= 0) return;
        const timerId = window.setInterval(() =>
            setResendCooldownRemaining(previous => Math.max(previous - 1, 0)), 1000);
        return () => window.clearInterval(timerId);
    }, [resendCooldownRemaining]);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedTab(data.value);
        setAccessView("list");
    };

    const onSendAccessCode = async () =>
    {
        if (!exchange)
        {
            setAccessCodeError("No Exchange selected. Close and reopen Manage access.");
            return;
        }
        if (sendingAccessCode || resendCooldownRemaining > 0) return;
        setAccessCodeStatus("");
        setAccessCodeError("");
        if (requireRecipientSignIn)
        {
            setAccessCodeError('Disable "Require recipient sign in" to send a no-auth access code.');
            return;
        }
        if (exchange.requestRecipientSignIn)
        {
            setAccessCodeError("Save your access changes first, then send the access code.");
            return;
        }
        setSendingAccessCode(true);
        try
        {
            await requestExchangeRecipientOtp(exchange.id);
            setAccessCodeStatus("Access code sent to recipient email.");
            setResendCooldownRemaining(RESEND_COOLDOWN_SECONDS);
        }
        catch (error: unknown)
        {
            const normalized = normalizeApiError(error, "Could not send access code. Please try again.");
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
    };

    const onUpdate = async () =>
    {
        if (!exchange) return;
        setUpdatingExchange(true);
        try
        {
            const parsedNoAuthValidityDays = Number(noAuthAccessValidityDays);
            if (!Number.isInteger(parsedNoAuthValidityDays)
                || parsedNoAuthValidityDays < 1
                || parsedNoAuthValidityDays > 30)
            {
                setAccessCodeError("No-auth access validity must be a whole number between 1 and 30 days.");
                return;
            }
            const request: UpdateExchangeRequest = {
                requireRecipientSignIn,
                allowDocumentAddition,
                allowDocumentDeletion,
                allowDocumentDownload,
                allowDocumentUpdate,
                allowDocumentUpload,
                allowedDownloadFormats: allowDocumentDownload ? allowedDownloadFormats ?? [] : [],
                noAuthAccessValidityDays: parsedNoAuthValidityDays,
            };
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
    };

    const onPrimaryRecipientReplaced = async () =>
    {
        setAccessView("list");
        if (!exchange) return;
        try
        {
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id);
            onExchangeAccessManagementUpdated(updatedExchange as ExchangeDetailedDto);
        }
        catch (error)
        {
            setDialogErrorMessage("Error refreshing access after replacing the recipient");
            console.error("Error refreshing access after replacing the recipient:", error);
        }
    };

    return {
        updatingExchange,
        sendingAccessCode,
        accessCodeStatus,
        accessCodeError,
        resendCooldownRemaining,
        requireRecipientSignIn,
        setRequireRecipientSignIn,
        allowDocumentAddition,
        setAllowDocumentAddition,
        allowDocumentDeletion,
        setAllowDocumentDeletion,
        allowDocumentDownload,
        setAllowDocumentDownload,
        allowDocumentUpdate,
        setAllowDocumentUpdate,
        allowDocumentUpload,
        setAllowDocumentUpload,
        allowedDownloadFormats,
        setAllowedDownloadFormats,
        noAuthAccessValidityDays,
        setNoAuthAccessValidityDays,
        selectedTab,
        accessView,
        setAccessView,
        dialogErrorMessage,
        onTabSelect,
        onSendAccessCode,
        onUpdate,
        onPrimaryRecipientReplaced,
    };
};

export type ExchangeAccessManagementDialogState = ReturnType<typeof useExchangeAccessManagementDialog>;
