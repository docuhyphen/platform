import {useEffect, useState} from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {AuthenticatorEnrollment, MfaConfiguration, MfaMethod} from "../../../models/models.tsx";
import {
    getMfaConfiguration,
    startAuthenticatorEnrollment,
    verifyAuthenticatorEnrollment
} from "../../../../services/appUserApi.ts";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import MfaConfigurationStep from "../../../settings/profile-tab/mfa-settings-dialog/MfaConfigurationStep.tsx";
import MfaEnrollmentStep from "../../../settings/profile-tab/mfa-settings-dialog/MfaEnrollmentStep.tsx";
import OnboardingMfaSetupActions from "./OnboardingMfaSetupActions.tsx";
import OnboardingMfaSetupHeader from "./OnboardingMfaSetupHeader.tsx";
import {useOnboardingMfaSetupStepStyles} from "./OnboardingMfaSetupStepStyles.tsx";

interface OnboardingMfaSetupStepProps
{
    onComplete: () => void;
    onSkip: () => void;
}
const getErrorMessage = (error: unknown): string =>
    (error as {errorMessage?: string})?.errorMessage || "Could not update multi-factor authentication.";
const OnboardingMfaSetupStep = ({onComplete, onSkip}: OnboardingMfaSetupStepProps) =>
{
    const styles = useOnboardingMfaSetupStepStyles();
    const globalStyles = useGlobalStyles();
    const {appUser, setAppUser} = useAuth();
    const [configuration, setConfiguration] = useState<MfaConfiguration>();
    const [provider, setProvider] = useState<Exclude<MfaMethod, 'EMAIL'>>('GOOGLE_AUTHENTICATOR');
    const [enrollment, setEnrollment] = useState<AuthenticatorEnrollment>();
    const [fallbackEnabled, setFallbackEnabled] = useState(false);
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string>();
    useEffect(() =>
    {
        getMfaConfiguration().then((value) =>
        {
            setConfiguration(value);
            setFallbackEnabled(value.emailFallbackEnabled);
        }).catch((reason: unknown) => setError(getErrorMessage(reason)));
    }, []);

    const updateUserMfaConfiguration = (value: MfaConfiguration) =>
    {
        if (!appUser) return;
        setAppUser({
            ...appUser,
            mfaMethod: value.method,
            emailMfaFallbackEnabled: value.emailFallbackEnabled,
        });
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
    const verifyEnrollment = async () =>
    {
        if (!enrollment) return;
        setBusy(true);
        setError(undefined);
        try
        {
            const updated = await verifyAuthenticatorEnrollment(enrollment.id, code, fallbackEnabled);
            updateUserMfaConfiguration(updated);
            onComplete();
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
    return (
        <div
            id={"individual-onboarding-mfa-step"}
            className={styles.container}>
            <OnboardingMfaSetupHeader headerClassName={styles.header}/>
            <div
                id={"individual-onboarding-mfa-content"}
                className={styles.content}>
                {!configuration && !error &&
                    <Spinner
                        id={"individual-onboarding-mfa-loading"}
                        label={"Loading security settings"}
                    />}
                {configuration && !enrollment &&
                    <MfaConfigurationStep
                        configuration={configuration}
                        provider={provider}
                        fallbackEnabled={fallbackEnabled}
                        disabled={busy}
                        showDefaultMethodCopy={false}
                        onProviderChange={setProvider}
                        onFallbackChange={setFallbackEnabled}
                        onSwitchToEmail={() => undefined}
                    />}
                {enrollment &&
                    <MfaEnrollmentStep
                        enrollment={enrollment}
                        code={code}
                        emailFallbackEnabled={fallbackEnabled}
                        disabled={busy}
                        showEmailFallbackToggle={false}
                        showManualSetupKey={false}
                        onCodeChange={setCode}
                        onFallbackChange={setFallbackEnabled}
                    />}
                {error &&
                    <Text
                        id={"individual-onboarding-mfa-error"}
                        className={styles.error}>
                        {error}
                    </Text>}
            </div>
            <OnboardingMfaSetupActions
                actionsClassName={styles.actions}
                buttonWithLoadingClassName={globalStyles.buttonWithLoading}
                busy={busy}
                canSubmit={Boolean(configuration) && (!enrollment || code.length === 6)}
                enrolling={Boolean(enrollment)}
                onPrimary={enrollment ? verifyEnrollment : beginEnrollment}
                onSkip={onSkip}
            />
        </div>
    );
};

export default OnboardingMfaSetupStep;
