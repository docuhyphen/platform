import React, {ChangeEvent, useEffect, useState} from 'react';
import {useAuth} from '../../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import {fetchAppUser, registerIndividual} from '../../services/userApi.ts';
import {AppUserDetailedDto, PersonDetailedDto, ResponseError} from '../models/models';
import useToken from "../../context/useToken.tsx";
import {
    Button,
    Checkbox,
    CheckboxOnChangeData,
    Dropdown,
    Field,
    Input,
    InputOnChangeData,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Option,
    OptionOnSelectData,
    SelectionEvents,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useIndividualRegistrationStyles} from "./IndividualRegistrationStyles.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import {useGlobalStyles} from "../../GlobalStyles.tsx";

//ToDo: change this based on country
const idTypes = [
    {key: 'ID_NUMBER', text: 'ID Number'},
    {key: 'PASSPORT_NUMBER', text: 'Passport Number'},
    {key: 'SOCIAL_SECURITY', text: 'Social Security'},
];

interface IndividualRegistrationProps
{
    onRegisterOrganizationChange: (registerOrganization: boolean) => void;
}

const IndividualRegistration: React.FC<IndividualRegistrationProps> = (
    {
        onRegisterOrganizationChange
    }
) =>
{
    const styles = useIndividualRegistrationStyles();
    const globalStyles = useGlobalStyles();

    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [identificationNumber, setIdentificationNumber] = useState('');
    const [idType, setIdType] = useState<string | undefined>('');
    const [alsoRegisterCompany, setAlsoRegisterCompany] = useState(false);
    const [registeringProfile, setRegisteringProfile] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const {setAppUser, appUser} = useAuth();
    const navigate = useNavigate();
    const token = useToken()

    useEffect(() =>
    {
        console.log("UseEffect of individual registration");
        if (appUser && appUser.person && !alsoRegisterCompany)
        {
            navigate('/sharing-sessions');
        }
    }, [appUser, navigate, alsoRegisterCompany]);

    const onFirstNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFirstName(newValue.value || '')
    }

    const onLastNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setLastName(newValue.value || '')
    }

    const onIdentificationNumberChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setIdentificationNumber(newValue.value || '')
    }

    const onIdTypeSelect = (_e: SelectionEvents, data: OptionOnSelectData) =>
    {
        setIdType(data.optionValue)
    }

    const onRegisterCompanyCheck = (_e: React.ChangeEvent<HTMLInputElement>, checked: CheckboxOnChangeData) =>
    {
        setAlsoRegisterCompany(checked.checked === true);
        onRegisterOrganizationChange(checked.checked === true);
    }

    const onRegisterIndividual = async () =>
    {
        if (registeringProfile)
        {
            return;
        }

        setErrorMessage('');
        setRegisteringProfile(true);

        try
        {
            const person = {firstName, lastName, idNumber: identificationNumber, idType};
            const personDetailedDto: PersonDetailedDto = (await registerIndividual(person, token)) as PersonDetailedDto;

            if (alsoRegisterCompany)
            {
                alert("Will also register company")
                navigate('/onboarding/company-registration');
            }
            else
            {
                alert("Will not register company")
                navigate('/sharing-sessions');
            }
        }
        catch (error)
        {
            console.error('Registration failed', error);
            // setErrorMessage((error as ResponseError)?.errorMessage);
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
                            onClick={() => setErrorMessage(undefined)}
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
            <Field
                label={"First Name"}
                validationState={"none"}
                validationMessage={""}>

                <Input type="text"
                       value={firstName}
                       onChange={onFirstNameChange}/>
            </Field>

            <Field
                label={"Last Name"}
                validationState={"none"}
                validationMessage={""}>

                <Input type="text"
                       value={lastName}
                       onChange={onLastNameChange}/>
            </Field>

            {alsoRegisterCompany &&
                <Field
                    label={"Identification Number"}
                    validationState={"none"}
                    validationMessage={""}>

                    <Input type="text"
                           value={identificationNumber}
                           onChange={onIdentificationNumberChange}/>
                </Field>
            }
            {alsoRegisterCompany &&


            <Field
                label={"Type of ID"}
                validationState={"none"}
                validationMessage={""}>

                <Dropdown onOptionSelect={onIdTypeSelect}>
                    {
                        idTypes.map((option) => (
                            <Option key={option.key} value={option.key}>
                                {option.text}
                            </Option>
                        ))}
                </Dropdown>
            </Field>
            }

            <Checkbox label="Register your organization as well"
                      checked={alsoRegisterCompany}
                      onChange={onRegisterCompanyCheck}/>

            <Button onClick={onRegisterIndividual}
                    shape={"circular"}
                    appearance={"primary"}
                    className={globalStyles.buttonWithLoading}>
                {registeringProfile &&
                    <Spinner size={"tiny"}/>
                }
                {
                    !registeringProfile &&
                    <Text>Register profile</Text>
                }
                {
                    registeringProfile &&
                    <Text>Registering profile</Text>
                }
            </Button>
        </div>
    );
};

export default IndividualRegistration;