import {Button, Radio, RadioGroup, Switch, Text} from "@fluentui/react-components";
import {MfaConfiguration, MfaMethod} from "../../../models/models.tsx";
import {useMfaSettingsDialogStyles} from "./MfaSettingsDialogStyles.tsx";

interface MfaConfigurationStepProps
{
    configuration: MfaConfiguration;
    provider: Exclude<MfaMethod, 'EMAIL'>;
    fallbackEnabled: boolean;
    disabled: boolean;
    onProviderChange: (provider: Exclude<MfaMethod, 'EMAIL'>) => void;
    onFallbackChange: (enabled: boolean) => void;
    onSwitchToEmail: () => void;
}

const MfaConfigurationStep = ({
    configuration,
    provider,
    fallbackEnabled,
    disabled,
    onProviderChange,
    onFallbackChange,
    onSwitchToEmail,
}: MfaConfigurationStepProps) =>
{
    const styles = useMfaSettingsDialogStyles();
    if (configuration.authenticatorConfigured)
    {
        return <>
            <Text id={"mfa-current-method"}>
                Your current method is {configuration.method === 'GOOGLE_AUTHENTICATOR'
                    ? 'Google Authenticator'
                    : 'Microsoft Authenticator'}.
            </Text>
            <Switch
                id={"mfa-email-fallback-switch"}
                checked={fallbackEnabled}
                disabled={disabled}
                label={"Allow email as a fallback when my authenticator app is unavailable"}
                onChange={(_, data) => onFallbackChange(data.checked)}
            />
            <div
                id={"mfa-switch-method-section"}
                className={styles.methodChange}>
                <Text
                    id={"mfa-switch-method-copy"}
                    className={styles.methodChangeCopy}>
                    Stop using your authenticator app and use email verification instead.
                </Text>
                <Button
                    id={"mfa-switch-to-email-button"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={disabled}
                    onClick={onSwitchToEmail}>
                    Switch to email MFA
                </Button>
            </div>
        </>;
    }

    return <>
        <Text id={"mfa-default-method-copy"}>
            Email is your current MFA method. Choose an authenticator app to set it up.
        </Text>
        <RadioGroup
            id={"mfa-provider-options"}
            className={styles.options}
            value={provider}
            onChange={(_, data) => onProviderChange(data.value as Exclude<MfaMethod, 'EMAIL'>)}>
            <Radio
                id={"mfa-provider-google"}
                value={"GOOGLE_AUTHENTICATOR"}
                label={"Google Authenticator"}
            />
            <Radio
                id={"mfa-provider-microsoft"}
                value={"MICROSOFT_AUTHENTICATOR"}
                label={"Microsoft Authenticator"}
            />
        </RadioGroup>
    </>;
};

export default MfaConfigurationStep;
