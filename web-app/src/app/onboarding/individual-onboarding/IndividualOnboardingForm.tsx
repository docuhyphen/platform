import React, {ChangeEvent, useEffect, useState} from 'react';
import {useAuth} from '../../../context/AuthContext.tsx';
import {useNavigate} from 'react-router-dom';
import {registerIndividual, updateAppUserSettings} from '../../../services/appUserApi.ts';
import {AppUserDetailedDto, AppUserSettingsDto, PersonDetailedDto, PersonRegistrationRequest, ResponseError} from '../../models/models.tsx';
import useToken from "../../../context/useToken.tsx";
import {
    Button,
    Checkbox,
    CheckboxOnChangeData,
    Field,
    Input,
    InputOnChangeData,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    OptionOnSelectData,
    Radio,
    RadioGroup,
    SelectionEvents,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useIndividualOnboardingFormStyles} from "./IndividualOnboardingFormStyles.tsx";
import {DismissRegular, WeatherMoonRegular, WeatherSunnyRegular} from "@fluentui/react-icons";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {useTheme} from "../../../context/themeContextBase.ts";
import type {ThemeMode} from "../../../context/theme.ts";

interface IndividualRegistrationProps
{
    onRegisterOrganizationChange: (registerOrganization: boolean) => void;
}

const IndividualOnboardingForm: React.FC<IndividualRegistrationProps> = ({onRegisterOrganizationChange}) =>
{
    const styles = useIndividualOnboardingFormStyles();
    const globalStyles = useGlobalStyles();
    const {mode: currentThemeMode, setMode} = useTheme();

    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [identificationNumber, setIdentificationNumber] = useState('');
    const [idType, setIdType] = useState<string | undefined>('');
    const [alsoRegisterOrganization, setAlsoRegisterOrganization] = useState(false);
    const [registeringProfile, setRegisteringProfile] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [selectedTheme, setSelectedTheme] = useState<ThemeMode>(currentThemeMode === 'system' ? 'light' : currentThemeMode);
    const {setAppUser, appUser} = useAuth();
    const navigate = useNavigate();
    const token = useToken();

    useEffect(() =>
    {
        if (appUser && appUser.person && !alsoRegisterOrganization)
        {
            navigate('/');
        }
    }, [appUser, navigate, alsoRegisterOrganization]);

    const onFirstNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFirstName(newValue.value || '');
    };

    const onLastNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setLastName(newValue.value || '');
    };

    const onIdentificationNumberChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setIdentificationNumber(newValue.value || '');
    };

    const onIdTypeSelect = (_e: SelectionEvents, data: OptionOnSelectData) =>
    {
        setIdType(data.optionValue);
    };

    const onThemeChange = (theme: ThemeMode) =>
    {
        setSelectedTheme(theme);
        setMode(theme);
    };

    const onRegisterOrganizationCheck = (_e: React.ChangeEvent<HTMLInputElement>, checked: CheckboxOnChangeData) =>
    {
        const isChecked = checked.checked === true;

        setAlsoRegisterOrganization(isChecked);
        onRegisterOrganizationChange(isChecked);

        if (!isChecked)
        {
            setIdType(undefined);
            setIdentificationNumber('');
        }
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
        // if (alsoRegisterOrganization && !identificationNumber.trim())
        // {
        //     setErrorMessage("Identification number is required.");
        //     return false;
        // }
        // if (alsoRegisterOrganization && !idType)
        // {
        //     setErrorMessage("ID type is required.");
        //     return false;
        // }
        return true;
    };

    const onRegisterIndividual = async () =>
    {
        if (registeringProfile)
        {
            return;
        }

        if (!validateInputs())
        {
            return;
        }

        setErrorMessage('');
        setRegisteringProfile(true);

        try
        {
            const person = {firstName, lastName, idNumber: identificationNumber, idType} as PersonRegistrationRequest;
            const registeredPerson = (await registerIndividual(person, token)) as PersonDetailedDto;

            const updatedUser = {...(appUser as AppUserDetailedDto), person: registeredPerson};
            setAppUser(updatedUser);

            // Persist the chosen theme to the server (best-effort — don't block navigation on failure)
            try
            {
                const baseSettings = appUser?.settings ?? {} as AppUserSettingsDto;
                await updateAppUserSettings({...baseSettings, theme: selectedTheme}, token);
            }
            catch (themeError)
            {
                console.warn('Failed to persist theme preference:', themeError);
            }

            if (alsoRegisterOrganization)
            {
                navigate('/onboarding/organization');
            }
            else
            {
                navigate('/sharing-sessions');
            }
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

    const renderErrorMessage = () => (
        errorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    {errorMessage}
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            onClick={() => setErrorMessage('')}
                            appearance="transparent"
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    return (
        <div className={styles.container}>
            {renderErrorMessage()}
            <Field label={"First Name"}
                   validationState={"none"}
                   validationMessage={""}>
                <Input type="text"
                       maxLength={60}
                       value={firstName}
                       onChange={onFirstNameChange}/>
            </Field>

            <Field label={"Last Name"}
                   validationState={"none"}
                   validationMessage={""}>
                <Input type="text"
                       maxLength={60}
                       value={lastName}
                       onChange={onLastNameChange}/>
            </Field>

            <Field label={"Choose your theme"}>
                <RadioGroup
                    value={selectedTheme}
                    onChange={(_, data) => onThemeChange(data.value as ThemeMode)}
                    layout="horizontal"
                    aria-label="Theme"
                >
                    <Radio
                        value="light"
                        label={<span style={{display: 'flex', alignItems: 'center', gap: '4px'}}><WeatherSunnyRegular/> Light</span>}
                    />
                    <Radio
                        value="dark"
                        label={<span style={{display: 'flex', alignItems: 'center', gap: '4px'}}><WeatherMoonRegular/> Dark</span>}
                    />
                </RadioGroup>
            </Field>

            {/*{alsoRegisterOrganization &&*/}
            {/*    <Field label={"Identification Number"}*/}
            {/*           validationState={"none"}*/}
            {/*           validationMessage={""}>*/}
            {/*        <Input type="text"*/}
            {/*               value={identificationNumber}*/}
            {/*               onChange={onIdentificationNumberChange}/>*/}
            {/*    </Field>*/}
            {/*}*/}
            {/*{alsoRegisterOrganization &&*/}
            {/*    <Field label={"Type of ID"}*/}
            {/*           validationState={"none"}*/}
            {/*           validationMessage={""}>*/}
            {/*        <Dropdown onOptionSelect={onIdTypeSelect}>*/}
            {/*            {idTypes.map((option) => (*/}
            {/*                <Option key={option.key} value={option.key}>*/}
            {/*                    {option.text}*/}
            {/*                </Option>*/}
            {/*            ))}*/}
            {/*        </Dropdown>*/}
            {/*    </Field>*/}
            {/*}*/}

            <Checkbox label="Register your organization as well"
                      checked={alsoRegisterOrganization}
                      onChange={onRegisterOrganizationCheck}/>

            <Button onClick={onRegisterIndividual}
                    shape={"circular"}
                    appearance={"primary"}
                    className={globalStyles.buttonWithLoading}>
                {registeringProfile && <Spinner size={"tiny"}/>}
                {!registeringProfile && <Text>Register profile</Text>}
                {registeringProfile && <Text>Registering profile</Text>}
            </Button>
        </div>
    );
};

export default IndividualOnboardingForm;