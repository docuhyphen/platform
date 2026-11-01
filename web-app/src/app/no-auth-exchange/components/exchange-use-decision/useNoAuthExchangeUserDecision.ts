import React, {useRef, useState} from "react";
import {ExchangeStatus, NoAuthExchangeBasicDto} from "../../../models/models.tsx";
import {fetchNoAuthExchange, updateNoAuthExchange} from "../../../../services/exchangeApi.ts";
import {getNoAuthOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import {DecisionStage, emptyDecisionOtp, MIN_DECLINE_REASON_LENGTH, OTP_LENGTH} from "./noAuthExchangeDecision.ts";

const NOT_AWAITING_DECISION_MESSAGE =
    'This sharing request is no longer awaiting a decision. Refresh the page for latest status.';
const REFRESH_FAILED_MESSAGE = 'Unable to refresh the request status. Please try again.';
const INCOMPLETE_OTP_MESSAGE = 'Enter the 6-digit verification code to continue.';

const useNoAuthExchangeUserDecision = (
    exchange: NoAuthExchangeBasicDto,
    onAccepted: (exchange: NoAuthExchangeBasicDto) => void,
    onDeclined: () => void,
) =>
{
    const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
    const [isAcceptDialogOpen, setIsAcceptDialogOpen] = useState<boolean>(false);
    const [isDeclineDialogOpen, setIsDeclineDialogOpen] = useState<boolean>(false);
    const [isAcceptingExchange, setIsAcceptingExchange] = useState<boolean>(false);
    const [isDecliningExchange, setIsDecliningExchange] = useState<boolean>(false);
    const [decisionOtp, setDecisionOtp] = useState<string[]>(emptyDecisionOtp());
    const [declineReason, setDeclineReason] = useState<string>('');
    const [otpRequestNotice, setOtpRequestNotice] = useState<string | undefined>(undefined);
    const [decisionStage, setDecisionStage] = useState<DecisionStage>('idle');
    const otpInputRefs = useRef<Array<HTMLInputElement | null>>([]);

    const openDecisionDialog = async (setDialogOpen: (open: boolean) => void) =>
    {
        try
        {
            const latestExchange = await fetchNoAuthExchange(exchange.id) as NoAuthExchangeBasicDto;
            if (latestExchange.status !== ExchangeStatus.INITIATED)
            {
                setErrorMessage(NOT_AWAITING_DECISION_MESSAGE);
                return;
            }

            setDialogOpen(true);
            setDecisionStage('otp-sent');
            window.setTimeout(() => otpInputRefs.current[0]?.focus(), 0);
        }
        catch (error: unknown)
        {
            const apiError = normalizeApiError(error, REFRESH_FAILED_MESSAGE);
            setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));
        }
    };

    const onAccept = () => openDecisionDialog(setIsAcceptDialogOpen);

    const onDecline = () => openDecisionDialog(setIsDeclineDialogOpen);

    const resetDecisionInput = () =>
    {
        setErrorMessage(undefined);
        setOtpRequestNotice(undefined);
        setDecisionOtp(emptyDecisionOtp());
        setDecisionStage('idle');
    };

    const onCancelAccept = () =>
    {
        resetDecisionInput();
        setIsAcceptDialogOpen(false);
    };

    const onCancelDecline = () =>
    {
        resetDecisionInput();
        setDeclineReason('');
        setIsDeclineDialogOpen(false);
    };

    const failValidation = (message: string) =>
    {
        setOtpRequestNotice(undefined);
        setErrorMessage(message);
        setDecisionStage('error');
    };

    const failRequest = (error: unknown, fallback: string) =>
    {
        setOtpRequestNotice(undefined);
        const apiError = normalizeApiError(error, fallback);
        setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));
        setDecisionStage(apiError.reasonCode === 'OTP_EXPIRED' ? 'expired' : 'error');
    };

    const onContinueAccept = async () =>
    {
        if (isAcceptingExchange)
        {
            return;
        }

        setIsAcceptingExchange(true);
        setErrorMessage(undefined);
        setDecisionStage('verifying');

        try
        {
            const otp = decisionOtp.join('');
            if (otp.length !== OTP_LENGTH)
            {
                failValidation(INCOMPLETE_OTP_MESSAGE);
                return;
            }

            const acceptedExchange = await updateNoAuthExchange(exchange.id, {
                otp,
                status: ExchangeStatus.ACCEPTED_STARTED
            });

            onAccepted(acceptedExchange as NoAuthExchangeBasicDto);
            setIsAcceptDialogOpen(false);
            setDecisionStage('idle');
        }
        catch (error: unknown)
        {
            failRequest(error, 'Failed to accept the request. Please try again later.');
        }
        finally
        {
            setIsAcceptingExchange(false);
        }
    };

    const onContinueDecline = async () =>
    {
        if (isDecliningExchange)
        {
            return;
        }

        setErrorMessage(undefined);
        setIsDecliningExchange(true);
        setDecisionStage('verifying');

        try
        {
            const otp = decisionOtp.join('');
            if (otp.length !== OTP_LENGTH)
            {
                failValidation(INCOMPLETE_OTP_MESSAGE);
                return;
            }

            const trimmedDeclineReason = declineReason.trim();
            if (trimmedDeclineReason.length < MIN_DECLINE_REASON_LENGTH)
            {
                failValidation(`Enter at least ${MIN_DECLINE_REASON_LENGTH} characters for the decline reason.`);
                return;
            }

            await updateNoAuthExchange(exchange.id, {
                otp,
                rejectReason: trimmedDeclineReason,
                status: ExchangeStatus.REJECTED
            });
            onDeclined();
            setIsDeclineDialogOpen(false);
            setDecisionStage('idle');
        }
        catch (error: unknown)
        {
            failRequest(error, 'Failed to decline the request. Please try again later.');
        }
        finally
        {
            setIsDecliningExchange(false);
        }
    };

    const handleOtpChange = (index: number, value: string) =>
    {
        const sanitizedValue = value.replace(/\D/g, '').slice(-1);
        const newOtp = [...decisionOtp];
        newOtp[index] = sanitizedValue;
        setDecisionOtp(newOtp);

        if (sanitizedValue && index < OTP_LENGTH - 1)
        {
            otpInputRefs.current[index + 1]?.focus();
        }
    };

    const handleOtpKeyDown = (index: number, key: string) =>
    {
        if (key === 'Backspace' && !decisionOtp[index] && index > 0)
        {
            otpInputRefs.current[index - 1]?.focus();
        }
    };

    const handleOtpPaste = (event: React.ClipboardEvent<HTMLInputElement>) =>
    {
        event.preventDefault();
        const pastedDigits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH);
        if (!pastedDigits)
        {
            return;
        }

        const nextOtp = Array.from({length: OTP_LENGTH}, (_, index) => pastedDigits[index] || '');
        setDecisionOtp(nextOtp);
        const nextFocusIndex = Math.min(pastedDigits.length, OTP_LENGTH - 1);
        otpInputRefs.current[nextFocusIndex]?.focus();
    };

    return {
        errorMessage,
        dismissError: () => setErrorMessage(undefined),
        isAcceptDialogOpen,
        isDeclineDialogOpen,
        isAcceptingExchange,
        isDecliningExchange,
        decisionOtp,
        declineReason,
        setDeclineReason,
        otpRequestNotice,
        decisionStage,
        otpInputRefs,
        onAccept,
        onDecline,
        onCancelAccept,
        onCancelDecline,
        onContinueAccept,
        onContinueDecline,
        handleOtpChange,
        handleOtpKeyDown,
        handleOtpPaste,
        isDecisionOtpComplete: decisionOtp.every((digit) => digit.trim().length === 1),
        isDeclineReasonValid: declineReason.trim().length >= MIN_DECLINE_REASON_LENGTH,
        isExchangeActionable: exchange.status === ExchangeStatus.INITIATED,
    };
};

export default useNoAuthExchangeUserDecision;
