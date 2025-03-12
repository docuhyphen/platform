import React, {ChangeEvent, useEffect, useState} from 'react';
import {useAuth} from '../../../context/AuthContext.tsx';
import {useNavigate} from 'react-router-dom';
import {registerIndividual} from '../../../services/userApi.ts';
import {AppUserDetailedDto, PersonDetailedDto, PersonRegistrationRequest, ResponseError} from '../../models/models.tsx';
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
    SelectionEvents,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useIndividualOnboardingFormStyles} from "./IndividualOnboardingFormStyles.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";

const idTypes = [
    {key: 'ID_NUMBER', text: 'ID Number'},
    {key: 'PASSPORT_NUMBER', text: 'Passport Number'},
    {key: 'SOCIAL_SECURITY', text: 'Social Security'},
];

interface IndividualRegistrationProps
{
    onRegisterOrganizationChange: (registerOrganization: boolean) => void;
}

const IndividualOnboardingForm: React.FC<IndividualRegistrationProps> = ({onRegisterOrganizationChange}) =>
{
    const styles = useIndividualOnboardingFormStyles();
    const globalStyles = useGlobalStyles();

    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [identificationNumber, setIdentificationNumber] = useState('');
    const [idType, setIdType] = useState<string | undefined>('');
    const [alsoRegisterOrganization, setAlsoRegisterOrganization] = useState(false);
    const [registeringProfile, setRegisteringProfile] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const {setAppUser, appUser} = useAuth();
    const navigate = useNavigate();
    const token = useToken();

    useEffect(() =>
    {
        if (appUser && appUser.person && !alsoRegisterOrganization)
        {
            console.log("Back")
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

            setAppUser({...(appUser as AppUserDetailedDto), person: registeredPerson});

            if (alsoRegisterOrganization)
            {
                alert("Onboarding org")
                navigate('/onboarding/organization');
            }
            else
            {
                alert("Onboarding Individual")
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
                       value={firstName}
                       onChange={onFirstNameChange}/>
            </Field>

            <Field label={"Last Name"}
                   validationState={"none"}
                   validationMessage={""}>
                <Input type="text"
                       value={lastName}
                       onChange={onLastNameChange}/>
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