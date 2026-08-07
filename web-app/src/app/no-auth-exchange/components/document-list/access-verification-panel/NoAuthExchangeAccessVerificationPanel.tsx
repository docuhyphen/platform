import React, {useState} from "react";
import {Button, Field, Input, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {verifyNoAuthExchangeAccessCode} from "../../../../../services/exchangeApi";
import {getErrorMessage, toActionableUploadError} from "../noAuthExchangeErrors";
import {useNoAuthExchangeAccessVerificationPanelStyles} from "./NoAuthExchangeAccessVerificationPanelStyles";

interface NoAuthExchangeAccessVerificationPanelProps
{
    exchangeId: string;
    /** Message explaining why verification is needed. Owned here so it is never duplicated. */
    reason: string;
    disabled: boolean;
    onVerified: () => void;
}

const NoAuthExchangeAccessVerificationPanel: React.FC<NoAuthExchangeAccessVerificationPanelProps> = (props) =>
{
    const styles = useNoAuthExchangeAccessVerificationPanelStyles();
    const [accessCode, setAccessCode] = useState<string>("");
    const [verifying, setVerifying] = useState<boolean>(false);
    const [error, setError] = useState<string>("");

    const onAccessCodeChange = (value: string) =>
    {
        setAccessCode(value.replace(/\D/g, "").slice(0, 8));
    };

    const onVerify = async () =>
    {
        if (verifying) return;

        const trimmedCode = accessCode.trim();
        if (trimmedCode.length < 4)
        {
            setError("Enter the access code that was resent by the requester, then verify again.");
            return;
        }

        setVerifying(true);
        setError("");
        try
        {
            await verifyNoAuthExchangeAccessCode(props.exchangeId, trimmedCode);
            setAccessCode("");
            props.onVerified();
        }
        catch (caught)
        {
            const message = getErrorMessage(caught, "Could not verify access code. Please try again.");
            setError(toActionableUploadError(message));
        }
        finally
        {
            setVerifying(false);
        }
    };

    const displayedMessage = error || props.reason;

    return (
        <section id={"no-auth-exchange-access-verification-panel"}
                 className={styles.verificationPanel}>
            <Text weight="semibold">Access verification required</Text>
            {displayedMessage && (
                <MessageBar id={"no-auth-exchange-access-verification-message"}
                            intent={error ? "error" : "warning"}>
                    <MessageBarBody className={styles.panelMessage}>{displayedMessage}</MessageBarBody>
                </MessageBar>
            )}
            <Text size={200}>
                Ask the requester to resend an access code in Manage Access, then enter that code here.
            </Text>
            <div className={styles.verificationControls}>
                <Field label="Access code"
                       className={styles.otpInputField}>
                    <Input id={"no-auth-exchange-access-code-input"}
                           value={accessCode}
                           onChange={(_, data) => onAccessCodeChange(data.value)}
                           placeholder="Enter access code"
                           inputMode="numeric"/>
                </Field>
                <Button id={"no-auth-exchange-verify-code-btn"}
                        appearance="primary"
                        shape="circular"
                        className={styles.verifyButton}
                        onClick={onVerify}
                        disabled={verifying || props.disabled}>
                    {verifying ? <Spinner size="tiny"/> : "Verify code"}
                </Button>
            </div>
        </section>
    );
};

export default NoAuthExchangeAccessVerificationPanel;

