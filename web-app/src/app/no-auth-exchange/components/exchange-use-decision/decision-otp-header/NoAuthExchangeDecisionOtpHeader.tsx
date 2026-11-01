import React from "react";
import {Field, Input, Text} from "@fluentui/react-components";
import {useNoAuthExchangeDecisionOtpHeaderStyles} from "./NoAuthExchangeDecisionOtpHeaderStyles.tsx";
import NoAuthExchangeDecisionErrorMessage from "../decision-error-message/NoAuthExchangeDecisionErrorMessage.tsx";
import {maskEmail, NoAuthExchangeDecisionOtpInputProps} from "../noAuthExchangeDecision.ts";

interface NoAuthExchangeDecisionOtpHeaderProps extends NoAuthExchangeDecisionOtpInputProps
{
    actionLabel: string;
    onSubmit: () => void;
}

const NoAuthExchangeDecisionOtpHeader: React.FC<NoAuthExchangeDecisionOtpHeaderProps> = (
    {
        actionLabel,
        onSubmit,
        recipientEmail,
        decisionStage,
        errorMessage,
        onDismissError,
        decisionOtp,
        otpInputRefs,
        onOtpChange,
        onOtpKeyDown,
        onOtpPaste
    }) =>
{
    const styles = useNoAuthExchangeDecisionOtpHeaderStyles();

    return (
        <>
            <Text id={"no-auth-exchange-user-decision-text-1"}
                  size={300}>
                Enter the 6-digit verification code sent to {maskEmail(recipientEmail)} to {actionLabel} this request.
            </Text>

            <Text id={"no-auth-exchange-user-decision-text-2"}
                  size={200}
                  className={styles.helperText}>
                {decisionStage === 'expired'
                    ? 'Your code has expired. Contact the person who sent this request to receive a new access code.'
                    : 'Use the verification code from the email you received when this request was sent.'}
            </Text>

            <NoAuthExchangeDecisionErrorMessage errorMessage={errorMessage}
                                                onDismiss={onDismissError}/>

            <div id={"no-auth-exchange-user-decision-div-1"}
                 className={styles.otpInputGroup}>
                {decisionOtp.map((otpDigit, index) => (
                    <Field id={`no-auth-exchange-user-decision-field-1-${index}`}
                           key={index}>
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
                               onPaste={onOtpPaste}
                               onKeyDown={(event) =>
                               {
                                   onOtpKeyDown(index, event.key);
                                   if (event.key === 'Enter')
                                   {
                                       onSubmit();
                                   }
                               }}
                               onChange={(event) => onOtpChange(index, event.target.value)}/>
                    </Field>
                ))}
            </div>
        </>
    );
};

export default NoAuthExchangeDecisionOtpHeader;
