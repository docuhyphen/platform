import {useEffect, useState} from "react";
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Text
} from "@fluentui/react-components";
import {AuthenticatorEnrollment, MfaConfiguration, MfaMethod} from "../../../models/models.tsx";
import {
    getMfaConfiguration,
    removeAuthenticatorEnrollment,
    startAuthenticatorEnrollment,
    updateMfaConfiguration,
    verifyAuthenticatorEnrollment
} from "../../../../services/appUserApi.ts";
import MfaEnrollmentStep from "./MfaEnrollmentStep.tsx";
import MfaConfigurationStep from "./MfaConfigurationStep.tsx";
import MfaDialogActions from "./MfaDialogActions.tsx";
import {useMfaSettingsDialogStyles} from "./MfaSettingsDialogStyles.tsx";
interface MfaSettingsDialogProps
{
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onUpdated: (configuration: MfaConfiguration) => void;
}
const getErrorMessage = (error: unknown): string =>
    (error as {errorMessage?: string})?.errorMessage || "Could not update multi-factor authentication.";
const MfaSettingsDialog = ({open, onOpenChange, onUpdated}: MfaSettingsDialogProps) =>
{
    const styles = useMfaSettingsDialogStyles();
    const [configuration, setConfiguration] = useState<MfaConfiguration>();
    const [provider, setProvider] = useState<Exclude<MfaMethod, 'EMAIL'>>('GOOGLE_AUTHENTICATOR');
    const [enrollment, setEnrollment] = useState<AuthenticatorEnrollment>();
    const [fallbackEnabled, setFallbackEnabled] = useState(false);
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string>();
    useEffect(() =>
    {
        if (!open) return;
        setEnrollment(undefined);
        setCode('');
        setError(undefined);
        getMfaConfiguration()
            .then((value) =>
            {
                setConfiguration(value);
                setFallbackEnabled(value.emailFallbackEnabled);
            })
            .catch((reason: unknown) => setError(getErrorMessage(reason)));
    }, [open]);

    const run = async (action: () => Promise<MfaConfiguration>) =>
    {
        setBusy(true);
        setError(undefined);
        try
        {
            const updated = await action();
            setConfiguration(updated);
            onUpdated(updated);
            onOpenChange(false);
        }
        catch (reason: unknown)
        {
            setError(getErrorMessage(reason));
        }
        finally
        {
            setBusy(false);
        }
    };
    const beginEnrollment = async () =>
    {
        setBusy(true);
        setError(undefined);
        try
        {
            setEnrollment(await startAuthenticatorEnrollment(provider));
        }
        catch (reason: unknown)
        {
            setError(getErrorMessage(reason));
        }
        finally
        {
            setBusy(false);
        }
    };

    const configured = configuration?.authenticatorConfigured === true;
    return <Dialog
        open={open}
        onOpenChange={(_, data) => !busy && onOpenChange(data.open)}>
        <DialogSurface
            id={"mfa-settings-dialog"}
            className={styles.surface}>
            <DialogBody id={"mfa-settings-dialog-body"}>
                <DialogTitle id={"mfa-settings-dialog-title"}>Multi-factor authentication</DialogTitle>
                <DialogContent
                    id={"mfa-settings-dialog-content"}
                    className={styles.content}>
                    {!configuration && !error && <Spinner
                        id={"mfa-settings-loading"}
                        label={"Loading security settings"}
                    />}
                    {configuration && !enrollment && <MfaConfigurationStep
                        configuration={configuration}
                        provider={provider}
                        fallbackEnabled={fallbackEnabled}
                        disabled={busy}
                        onProviderChange={setProvider}
                        onFallbackChange={setFallbackEnabled}
                        onSwitchToEmail={() => run(removeAuthenticatorEnrollment)}
                    />}
                    {enrollment && <MfaEnrollmentStep
                        enrollment={enrollment}
                        code={code}
                        emailFallbackEnabled={fallbackEnabled}
                        disabled={busy}
                        onCodeChange={setCode}
                        onFallbackChange={setFallbackEnabled}
                    />}
                    {error && <Text
                        id={"mfa-settings-error"}
                        className={styles.error}>
                        {error}
                    </Text>}
                </DialogContent>
                {configuration && <MfaDialogActions
                    configured={configured}
                    enrolling={Boolean(enrollment)}
                    busy={busy}
                    codeComplete={code.length === 6}
                    onPrimary={enrollment
                        ? () => run(() => verifyAuthenticatorEnrollment(enrollment.id, code, fallbackEnabled))
                        : (configured
                            ? () => run(() => updateMfaConfiguration(fallbackEnabled))
                            : beginEnrollment)}
                    onCancel={() => onOpenChange(false)}
                />}
            </DialogBody>
        </DialogSurface>
    </Dialog>;
};

export default MfaSettingsDialog;
