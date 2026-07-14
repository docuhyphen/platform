import {useEffect, useState} from "react";
import {Field, Input, Switch, Text} from "@fluentui/react-components";
import QRCode from "qrcode";
import {AuthenticatorEnrollment} from "../../../models/models.tsx";
import {useMfaSettingsDialogStyles} from "./MfaSettingsDialogStyles.tsx";

interface MfaEnrollmentStepProps
{
    enrollment: AuthenticatorEnrollment;
    code: string;
    emailFallbackEnabled: boolean;
    disabled: boolean;
    onCodeChange: (value: string) => void;
    onFallbackChange: (enabled: boolean) => void;
}

const MfaEnrollmentStep = ({
    enrollment,
    code,
    emailFallbackEnabled,
    disabled,
    onCodeChange,
    onFallbackChange,
}: MfaEnrollmentStepProps) =>
{
    const styles = useMfaSettingsDialogStyles();
    const [qrCodeUrl, setQrCodeUrl] = useState<string>();

    useEffect(() =>
    {
        let active = true;
        QRCode.toDataURL(enrollment.otpauthUri, {width: 440, margin: 1})
            .then((url) => active && setQrCodeUrl(url));
        return () =>
        {
            active = false;
        };
    }, [enrollment.otpauthUri]);

    return <div
        id={"mfa-enrollment-content"}
        className={styles.enrollment}>
        <Text id={"mfa-enrollment-instructions"}>
            Scan this code with your selected authenticator app, then enter its current 6-digit code.
        </Text>
        {qrCodeUrl && <img
            id={"mfa-enrollment-qr-code"}
            className={styles.qrCode}
            src={qrCodeUrl}
            alt={"Authenticator enrollment QR code"}
        />}
        <Text
            id={"mfa-enrollment-manual-label"}
            size={200}>
            If scanning does not work, enter this setup key manually:
        </Text>
        <Text
            id={"mfa-enrollment-secret"}
            className={styles.secret}>
            {enrollment.secret}
        </Text>
        <Field
            id={"mfa-enrollment-code-field"}
            label={"Authenticator code"}
            required>
            <Input
                id={"mfa-enrollment-code-input"}
                value={code}
                maxLength={6}
                inputMode={"numeric"}
                autoComplete={"one-time-code"}
                disabled={disabled}
                onChange={(_, data) => onCodeChange(data.value.replace(/\D/g, ''))}
            />
        </Field>
        <Switch
            id={"mfa-email-fallback-enrollment-switch"}
            className={styles.enrollmentSwitch}
            checked={emailFallbackEnabled}
            disabled={disabled}
            label={"Allow email as a fallback when my authenticator app is unavailable"}
            onChange={(_, data) => onFallbackChange(data.checked)}
        />
    </div>;
};

export default MfaEnrollmentStep;
