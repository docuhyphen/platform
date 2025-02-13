import React, {ChangeEvent, useEffect, useState} from 'react';
import {useAuth} from '../../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import {registerIndividual} from '../../services/api';
import {AppUser} from '../models/models';
import './IndividualRegistration.css';
import useToken from "../../context/useToken.tsx";
import {
    Button,
    Checkbox,
    CheckboxOnChangeData,
    Dropdown,
    Field,
    Input,
    InputOnChangeData,
    Option,
    OptionOnSelectData,
    SelectionEvents
} from "@fluentui/react-components";

//ToDo: change this based on country
const idTypes = [
    {key: 'ID_NUMBER', text: 'ID Number'},
    {key: 'PASSPORT_NUMBER', text: 'Passport Number'},
    {key: 'SOCIAL_SECURITY', text: 'Social Security'},
];

const IndividualRegistration: React.FC = () =>
{
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [identificationNumber, setIdentificationNumber] = useState('');
    const [idType, setIdType] = useState<string | undefined>('');
    const [alsoRegisterCompany, setAlsoRegisterCompany] = useState(false);
    const {setAppUser, appUser} = useAuth();
    const navigate = useNavigate();
    const token = useToken()

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

        console.log("Checked1: ", checked.checked);
    }

    const onRegisterIndividual = async () =>
    {
        console.log("Registering individual");
        console.log("Checked: ", alsoRegisterCompany);

        try
        {
            const person = {firstName, lastName, idNumber: identificationNumber, idType};
            const updatedUser: AppUser = await registerIndividual(person, token); // Assume this API call returns the updated user

            setAppUser(updatedUser);

            if (alsoRegisterCompany)
            {
                alert("Will also register company")
                navigate('/onboarding/company-registration');
            }
            else
            {
                alert("Will not register company")
                navigate('/landing');
            }
        }
        catch (error)
        {
            console.error('Registration failed', error);
        }
    };

    useEffect(() =>
    {
        console.log("UseEffect of individual registration");
        if (appUser && appUser.person && !alsoRegisterCompany)
        {
            navigate('/landing');
        }
    }, [appUser, navigate, alsoRegisterCompany]);
    return (
        <div>
            <h1>Individual Registration</h1>

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

            <Field
                label={"Identification Number"}
                validationState={"none"}
                validationMessage={""}>

                <Input type="text"
                       value={identificationNumber}
                       onChange={onIdentificationNumberChange}/>
            </Field>

            <Field
                label={"Type of ID"}
                validationState={"none"}
                validationMessage={""}>

                <Dropdown id="idType"
                          onOptionSelect={onIdTypeSelect}>
                    {
                        idTypes.map((option) => (
                            <Option key={option.key} value={option.key}>
                                {option.text}
                            </Option>
                        ))}
                </Dropdown>
            </Field>

            <Checkbox label="Register a company"
                      checked={alsoRegisterCompany}
                      onChange={onRegisterCompanyCheck}/>

            <Button onClick={onRegisterIndividual}> Register </Button>
        </div>
    );
};

export default IndividualRegistration;