import React from "react";

export type DecisionStage = 'idle' | 'otp-sent' | 'verifying' | 'expired' | 'error';

export const OTP_LENGTH = 6;
export const MIN_DECLINE_REASON_LENGTH = 10;

export const emptyDecisionOtp = (): string[] => ['', '', '', '', '', ''];

export interface NoAuthExchangeDecisionOtpInputProps
{
    recipientEmail: string | undefined;
    decisionStage: DecisionStage;
    errorMessage: string | undefined;
    onDismissError: () => void;
    decisionOtp: string[];
    otpInputRefs: React.RefObject<Array<HTMLInputElement | null>>;
    onOtpChange: (index: number, value: string) => void;
    onOtpKeyDown: (index: number, key: string) => void;
    onOtpPaste: (event: React.ClipboardEvent<HTMLInputElement>) => void;
}

export const maskEmail = (email: string | undefined): string =>
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
};
