import React, {useEffect, useRef, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Link,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Text,
    Textarea
} from "@fluentui/react-components";
import {useNoAuthExchangeDocumentListStyles} from "./NoAuthExchangeUserDecisionStyles.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import {NoAuthExchangeBasicDto, ExchangeStatus} from "../../../models/models.tsx";
import {fetchNoAuthExchange, requestNoAuthExchangeOtp, updateNoAuthExchange} from "../../../../services/exchangeApi.ts";
import {getNoAuthOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";

interface NoAuthExchangeUserDecisionProps
{
    exchange: NoAuthExchangeBasicDto;
    onAccepted: (exchange: NoAuthExchangeBasicDto) => void;
    onDeclined: () => void;
}

const NoAuthExchangeUserDecision: React.FC<NoAuthExchangeUserDecisionProps> = (
    {
        exchange,
        onAccepted,
        onDeclined
    }) =>
{
    const minDeclineReasonLength = 10;
    const otpLength = 6;
    const defaultResendCooldownSeconds = 30;

    type DecisionStage = 'idle' | 'requesting-otp' | 'otp-sent' | 'verifying' | 'expired' | 'error';

    const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
    const [isAcceptDialogOpen, setIsAcceptDialogOpen] = useState<boolean>(false);
    const [isDeclineDialogOpen, setIsDeclineDialogOpen] = useState<boolean>(false);
    const [isAcceptingExchange, setIsAcceptingExchange] = useState<boolean>(false);
    const [isDecliningExchange, setIsDecliningExchange] = useState<boolean>(false);
    const [decisionOtp, setDecisionOtp] = useState<string[]>(['', '', '', '', '', '']);
    const [declineReason, setDeclineReason] = useState<string>('');
    const [isRequestingOtp, setIsRequestingOtp] = useState<boolean>(false);
    const [otpRequestNotice, setOtpRequestNotice] = useState<string | undefined>(undefined);
    const [decisionStage, setDecisionStage] = useState<DecisionStage>('idle');
    const [resendCooldownRemainingSeconds, setResendCooldownRemainingSeconds] = useState<number>(0);
    const styles = useNoAuthExchangeDocumentListStyles();
    const otpInputRefs = useRef<Array<HTMLInputElement | null>>([]);

    useEffect(() =>
    {
        if (resendCooldownRemainingSeconds <= 0)
        {
            return;
        }

        const timer = window.setInterval(() =>
        {
            setResendCooldownRemainingSeconds((previous) => Math.max(0, previous - 1));
        }, 1000);

        return () => window.clearInterval(timer);
    }, [resendCooldownRemainingSeconds]);

    const maskEmail = (email: string | undefined): string =>
    {
        if (!email || !email.includes('@'))
        {
            return 'your email address';
        }

        const [name, domain] = email.split('@');
        if (!name)
        {
            return `***@${domain}`;
        }

        return `${name[0]}***@${domain}`;
    }

    const formatCooldown = (seconds: number): string =>
    {
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
    }

    const onAccept = async () =>
    {
        try
        {
            const latestExchange = await fetchNoAuthExchange(exchange.id) as NoAuthExchangeBasicDto;
            if (latestExchange.status !== ExchangeStatus.INITIATED)
            {
                setErrorMessage('This sharing request is no longer awaiting a decision. Refresh the page for latest status.');
                return;
            }

            setIsAcceptDialogOpen(true);
            setDecisionStage('requesting-otp');
            // Auto-request an OTP on first opening; subsequent re-sends are explicit.
            await requestOtp();
            window.setTimeout(() => otpInputRefs.current[0]?.focus(), 0);
        }
        catch (error: unknown)
        {
            const apiError = normalizeApiError(error, 'Unable to refresh the request status. Please try again.');
            setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));
        }
    }

    const requestOtp = async () =>
    {
        if (isRequestingOtp || resendCooldownRemainingSeconds > 0) return;
        setIsRequestingOtp(true);
        setErrorMessage(undefined);
        setOtpRequestNotice(undefined);
        try
        {
            await requestNoAuthExchangeOtp(exchange.id);
            setDecisionStage('otp-sent');
            setOtpRequestNotice("Verification code sent. Check your email.");
            setResendCooldownRemainingSeconds(defaultResendCooldownSeconds);
        }
        catch (error: unknown)
        {
            const apiError = normalizeApiError(error, "Could not send a verification code. Please try again.");
            setDecisionStage('error');
            setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));

            if (typeof apiError.retryAfterSeconds === 'number' && apiError.retryAfterSeconds > 0)
            {
                setResendCooldownRemainingSeconds(Math.ceil(apiError.retryAfterSeconds));
            }
        }
        finally
        {
            setIsRequestingOtp(false);
        }
    }

    const onDecline = async () =>
    {
        try
        {
            const latestExchange = await fetchNoAuthExchange(exchange.id) as NoAuthExchangeBasicDto;
            if (latestExchange.status !== ExchangeStatus.INITIATED)
            {
                setErrorMessage('This sharing request is no longer awaiting a decision. Refresh the page for latest status.');
                return;
            }

            setIsDeclineDialogOpen(true);
            setDecisionStage('requesting-otp');
            // Decline also requires OTP, same as accept.
            await requestOtp();
            window.setTimeout(() => otpInputRefs.current[0]?.focus(), 0);
        }
        catch (error: unknown)
        {
            const apiError = normalizeApiError(error, 'Unable to refresh the request status. Please try again.');
            setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));
        }
    }

    const onCancelAccept = () =>
    {
        setErrorMessage(undefined);
        setOtpRequestNotice(undefined);
        setDecisionOtp(['', '', '', '', '', '']);
        setDecisionStage('idle');
        setResendCooldownRemainingSeconds(0);
        setIsAcceptDialogOpen(false);
    }

    const onCancelDecline = () =>
    {
        setErrorMessage(undefined);
        setOtpRequestNotice(undefined);
        setDecisionOtp(['', '', '', '', '', '']);
        setDeclineReason('');
        setDecisionStage('idle');
        setResendCooldownRemainingSeconds(0);
        setIsDeclineDialogOpen(false);
    }

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
            if (otp.length !== otpLength)
            {
                setOtpRequestNotice(undefined);
                setErrorMessage('Enter the 6-digit verification code to continue.');
                setDecisionStage('error');
                return;
            }

            const acceptRequest = {
                otp,
                status: ExchangeStatus.ACCEPTED_STARTED
            };

            const acceptedExchange = await updateNoAuthExchange(exchange.id, acceptRequest);

            onAccepted(acceptedExchange as NoAuthExchangeBasicDto);
            setIsAcceptDialogOpen(false);
            setDecisionStage('idle');
        }
        catch (error: unknown)
        {
            setOtpRequestNotice(undefined);
            const apiError = normalizeApiError(error, 'Failed to accept the request. Please try again later.');
            setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));
            setDecisionStage(apiError.reasonCode === 'OTP_EXPIRED' ? 'expired' : 'error');
            if (typeof apiError.retryAfterSeconds === 'number' && apiError.retryAfterSeconds > 0)
            {
                setResendCooldownRemainingSeconds(Math.ceil(apiError.retryAfterSeconds));
            }
        }
        finally
        {
            setIsAcceptingExchange(false);
        }
    }

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
            if (otp.length !== otpLength)
            {
                setOtpRequestNotice(undefined);
                setErrorMessage('Enter the 6-digit verification code to continue.');
                setDecisionStage('error');
                return;
            }

            const trimmedDeclineReason = declineReason.trim();
            if (trimmedDeclineReason.length < minDeclineReasonLength)
            {
                setOtpRequestNotice(undefined);
                setErrorMessage(`Enter at least ${minDeclineReasonLength} characters for the decline reason.`);
                setDecisionStage('error');
                return;
            }

            const declineRequest = {
                otp,
                rejectReason: trimmedDeclineReason,
                status: ExchangeStatus.REJECTED
            }

            await updateNoAuthExchange(exchange.id, declineRequest);
            onDeclined();
            setIsDeclineDialogOpen(false);
            setDecisionStage('idle');
        }
        catch (error: unknown)
        {
            setOtpRequestNotice(undefined);
            const apiError = normalizeApiError(error, 'Failed to decline the request. Please try again later.');
            setErrorMessage(getNoAuthOtpFriendlyMessage(apiError));
            setDecisionStage(apiError.reasonCode === 'OTP_EXPIRED' ? 'expired' : 'error');
            if (typeof apiError.retryAfterSeconds === 'number' && apiError.retryAfterSeconds > 0)
            {
                setResendCooldownRemainingSeconds(Math.ceil(apiError.retryAfterSeconds));
            }
        }
        finally
        {
            setIsDecliningExchange(false);
        }
    }

    const renderErrorMessage = () => (
        errorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    {errorMessage}
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            id={"no-auth-exchange-decision-dismiss-error-btn"}
                            onClick={() => setErrorMessage(undefined)}
                            appearance="transparent"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const handleOtpChange = (index: number, value: string) =>
    {
        const sanitizedValue = value.replace(/\D/g, '').slice(-1);
        const newOtp = [...decisionOtp];
        newOtp[index] = sanitizedValue;
        setDecisionOtp(newOtp);

        if (sanitizedValue && index < otpLength - 1)
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
        const pastedDigits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, otpLength);
        if (!pastedDigits)
        {
            return;
        }

        const nextOtp = Array.from({length: otpLength}, (_, index) => pastedDigits[index] || '');
        setDecisionOtp(nextOtp);
        const nextFocusIndex = Math.min(pastedDigits.length, otpLength - 1);
        otpInputRefs.current[nextFocusIndex]?.focus();
    };

    const isDecisionOtpComplete = decisionOtp.every((digit) => digit.trim().length === 1);
    const isDeclineReasonValid = declineReason.trim().length >= minDeclineReasonLength;
    const isExchangeActionable = exchange.status === ExchangeStatus.INITIATED;
    const resendDisabled = isRequestingOtp || resendCooldownRemainingSeconds > 0;

    const renderOtpHeader = (actionLabel: string) => (
        <>
            <Text size={300}>
                Enter the 6-digit verification code sent to {maskEmail(exchange.recipientEmail)} to {actionLabel} this request.
            </Text>

            <Text size={200} className={styles.helperText}>
                {decisionStage === 'expired'
                    ? 'Your code expired. Request a new code to continue.'
                    : 'Codes expire after 10 minutes and can only be used once.'}
            </Text>

            {renderErrorMessage()}

            {otpRequestNotice && !errorMessage && (
                <MessageBar intent={"success"}>
                    <MessageBarBody>{otpRequestNotice}</MessageBarBody>
                </MessageBar>
            )}

            <Link as="button"
                  onClick={requestOtp}
                  disabled={resendDisabled}>
                {isRequestingOtp
                    ? "Sending..."
                    : resendCooldownRemainingSeconds > 0
                        ? `Resend verification code (${formatCooldown(resendCooldownRemainingSeconds)})`
                        : "Resend verification code"}
            </Link>

            <div className={styles.otpInputGroup}>
                {decisionOtp.map((otpDigit, index) => (
                    <Field key={index}>
                        <Input id={`no-auth-exchange-decision-otp-input-${index}`}
                               className={styles.otpInput}
                               maxLength={1}
                               inputMode={"numeric"}
                               pattern={"[0-9]*"}
                               value={otpDigit}
                               aria-label={`Verification code digit ${index + 1}`}
                               ref={(element) =>
                               {
                                   otpInputRefs.current[index] = element;
                               }}
                               onPaste={handleOtpPaste}
                               onKeyDown={(event) =>
                               {
                                   handleOtpKeyDown(index, event.key);
                                   if (event.key === 'Enter')
                                   {
                                       if (isDeclineDialogOpen)
                                       {
                                           void onContinueDecline();
                                       }
                                       else
                                       {
                                           void onContinueAccept();
                                       }
                                   }
                               }}
                               onChange={(event) => handleOtpChange(index, event.target.value)}/>
                    </Field>
                ))}
            </div>
        </>
    );

    return (
        <>
            <section className={styles.container}>
                <Text size={500}
                      align={"center"}>
                    {exchange.initiatorFirstName} {exchange.initiatorLastName} has requested documents from you.
                </Text>
                {exchange.initialShareMessage &&
                    <Text size={300} align={"center"}>
                        {exchange.initialShareMessage}
                    </Text>
                }
                <div className={styles.decisionActions}>
                    <Button id={"no-auth-exchange-decision-accept-btn"}
                            onClick={onAccept}
                            disabled={!isExchangeActionable}
                            shape={"circular"}
                            appearance={"primary"}
                            size={"large"}>
                        Accept
                    </Button>
                    <Button id={"no-auth-exchange-decision-decline-btn"}
                            onClick={onDecline}
                            disabled={!isExchangeActionable}
                            className={styles.declineButton}
                            shape={"circular"}
                            appearance={"subtle"}
                            size={"large"}>
                        Decline
                    </Button>
                </div>
                {!isExchangeActionable &&
                    <MessageBar intent={"warning"}>
                        <MessageBarBody>
                            This sharing request is no longer awaiting a decision.
                        </MessageBarBody>
                    </MessageBar>
                }
                <div
                    className={styles.termsAndConditions}>
                    <Text size={300}
                          align={"center"}>
                        By accepting, you agree to the terms of the Doc-Hyphen app document sharing.
                    </Text>
                    <Text size={300}
                          align={"center"}>
                        Don't recognise the sender? report <Link>here</Link>
                    </Text>
                </div>
                <Dialog open={isDeclineDialogOpen} modalType={"alert"}>
                    <DialogSurface>
                        <DialogBody>
                            <DialogTitle>Declining</DialogTitle>
                            <DialogContent className={styles.declineDialogContent}>
                                {renderOtpHeader('decline')}

                                <Field>
                                    <Textarea id={"no-auth-exchange-decline-reason-textarea"}
                                              placeholder={"Reason for declining"}
                                              value={declineReason}
                                              onChange={(e) => setDeclineReason(e.target.value)}
                                              maxLength={100}
                                              minLength={10}/>
                                </Field>
                                <Text size={200} className={styles.helperText}>
                                    {declineReason.trim().length}/{minDeclineReasonLength} minimum characters
                                </Text>
                            </DialogContent>
                        </DialogBody>
                        <DialogActions>
                            <DialogTrigger>
                                <Button id={"no-auth-exchange-decline-confirm-btn"}
                                        onClick={onContinueDecline}
                                        disabled={isDecliningExchange || !isDecisionOtpComplete || !isDeclineReasonValid}
                                        shape={"circular"}
                                        appearance={"primary"}>
                                    Decline
                                </Button>
                            </DialogTrigger>
                            <Button id={"no-auth-exchange-decline-cancel-btn"}
                                    onClick={onCancelDecline}
                                    shape={"circular"}
                                    appearance={"subtle"}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogSurface>
                </Dialog>
                <Dialog open={isAcceptDialogOpen}>
                    <DialogSurface>
                        <DialogBody>
                            <DialogTitle>Accepting</DialogTitle>
                            <DialogContent className={styles.acceptDialogContent}>
                                {renderOtpHeader('accept')}
                            </DialogContent>
                        </DialogBody>
                        <DialogActions>
                            <DialogTrigger>
                                <Button id={"no-auth-exchange-accept-confirm-btn"}
                                        onClick={onContinueAccept}
                                        disabled={isAcceptingExchange || !isDecisionOtpComplete}
                                        shape={"circular"}
                                        appearance={"primary"}>
                                    Accept
                                </Button>
                            </DialogTrigger>
                            <Button id={"no-auth-exchange-accept-cancel-btn"}
                                    onClick={onCancelAccept}
                                    shape={"circular"}
                                    appearance={"subtle"}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogSurface>
                </Dialog>
                <div aria-live={"polite"} className={styles.srOnly}>{otpRequestNotice || errorMessage || ''}</div>
            </section>
        </>
    );
}

export default NoAuthExchangeUserDecision;