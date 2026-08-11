import {ChangeEvent, useState} from 'react';
import {CheckboxOnChangeData, InputOnChangeData} from "@fluentui/react-components";
import {useAuth} from '../../../context/AuthContext.tsx';
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {useTheme} from "../../../context/themeContextBase.ts";
import type {ThemeMode} from "../../../context/theme.ts";
import useToken from "../../../context/useToken.tsx";
import {registerIndividual, updateAppUserSettings} from '../../../services/appUserApi.ts';
import {AppUserDetailedDto, AppUserSettingsDto, PersonDetailedDto, PersonRegistrationRequest, ResponseError} from '../../models/models.tsx';
import {useIndividualOnboardingFormStyles} from "./IndividualOnboardingFormStyles.tsx";
import IndividualOnboardingErrorBar from "./IndividualOnboardingErrorBar.tsx";
import IndividualOnboardingProfileFields from "./IndividualOnboardingProfileFields.tsx";
import IndividualOnboardingSubmitSection from "./IndividualOnboardingSubmitSection.tsx";
import IndividualOnboardingThemeField from "./IndividualOnboardingThemeField.tsx";

interface IndividualRegistrationProps
{
    onRegisterOrganizationChange: (registerOrganization: boolean) => void;
    onProfileRegistered: () => void;
}

const IndividualOnboardingForm: React.FC<IndividualRegistrationProps> = ({
    onRegisterOrganizationChange,
    onProfileRegistered
}) =>
{
    const styles = useIndividualOnboardingFormStyles();
    const globalStyles = useGlobalStyles();
    const {mode: currentThemeMode, setMode} = useTheme();
    const {setAppUser, appUser} = useAuth();
    const token = useToken();
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [alsoRegisterOrganization, setAlsoRegisterOrganization] = useState(false);
    const [registeringProfile, setRegisteringProfile] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [selectedTheme, setSelectedTheme] = useState<ThemeMode>(
        currentThemeMode === 'system' ? 'light' : currentThemeMode
    );

    const onFirstNameChange = (_e: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
        setFirstName(data.value || '');

    const onLastNameChange = (_e: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
        setLastName(data.value || '');

    const onThemeChange = (theme: ThemeMode) =>
    {
        setSelectedTheme(theme);
        setMode(theme);
    };

    const onRegisterOrganizationCheck = (
        _e: ChangeEvent<HTMLInputElement>,
        data: CheckboxOnChangeData
    ) =>
    {
        const isChecked = data.checked === true;
        setAlsoRegisterOrganization(isChecked);
        onRegisterOrganizationChange(isChecked);
    };

    const validateInputs = () =>
    {
        if (!firstName.trim())
        {
            setErrorMessage("First name is required.");
            return false;
        }
        if (!lastName.trim())
        {
            setErrorMessage("Last name is required.");
            return false;
        }
        return true;
    };

    const persistTheme = async () =>
    {
        try
        {
            const baseSettings = appUser?.settings ?? ({} as AppUserSettingsDto);
            await updateAppUserSettings({...baseSettings, theme: selectedTheme}, token);
        }
        catch (themeError)
        {
            console.warn('Failed to persist theme preference:', themeError);
        }
    };

    const onRegisterIndividual = async () =>
    {
        if (registeringProfile || !validateInputs()) return;
        setErrorMessage('');
        setRegisteringProfile(true);

        try
        {
            const person = {firstName, lastName} as PersonRegistrationRequest;
            const registeredPerson = await registerIndividual(person, token) as PersonDetailedDto;
            setAppUser({
                ...(appUser as AppUserDetailedDto),
                person: registeredPerson,
            });
            await persistTheme();
            onProfileRegistered();
        }
        catch (error)
        {
            console.error('Registration failed', error);
            setErrorMessage((error as ResponseError)?.errorMessage || "Registration failed.");
        }
        finally
        {
            setRegisteringProfile(false);
        }
    };

    return (
        <div
            id={"individual-onboarding-form"}
            className={styles.container}>
            <IndividualOnboardingErrorBar
                message={errorMessage}
                onDismiss={() => setErrorMessage('')}
            />
            <IndividualOnboardingProfileFields
                firstName={firstName}
                lastName={lastName}
                onFirstNameChange={onFirstNameChange}
                onLastNameChange={onLastNameChange}
            />
            <IndividualOnboardingThemeField
                radioLabelClassName={styles.radioLabel}
                selectedTheme={selectedTheme}
                onThemeChange={onThemeChange}
            />
            <IndividualOnboardingSubmitSection
                alsoRegisterOrganization={alsoRegisterOrganization}
                buttonWithLoadingClassName={globalStyles.buttonWithLoading}
                registeringProfile={registeringProfile}
                onRegisterIndividual={onRegisterIndividual}
                onRegisterOrganizationChange={onRegisterOrganizationCheck}
            />
        </div>
    );
};

export default IndividualOnboardingForm;
