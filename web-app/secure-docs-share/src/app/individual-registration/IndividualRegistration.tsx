import React, {useState} from 'react';
import {useAuth} from '../../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import {registerIndividual} from '../../services/api'; // Assume this API call exists
import {AppUser} from '../models/models'; // Assume this model exists
import './IndividualRegistration.css';
import useToken from "../../context/useToken.tsx";
import {Button, Checkbox, Dropdown, Input, Label, Option} from "@fluentui/react-components";

const idTypes = [
    {key: 'ID_NUMBER', text: 'ID Number'},
    {key: 'PASSPORT_NUMBER', text: 'Passport Number'},
    {key: 'SOCIAL_SECURITY', text: 'Social Security'},
];

const IndividualRegistration: React.FC = () => {
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [idNumber, setIdNumber] = useState('');
    const [idType, setIdType] = useState(idTypes[0].key as string);
    const [isCompany, setIsCompany] = useState(false);
    const {setAppUser} = useAuth();
    const navigate = useNavigate();
    const token = useToken()

    const onRegisterIndividual = async () =>
    {
        try
        {
            const person = {firstName, lastName, idNumber, idType};
            const updatedUser: AppUser = await registerIndividual(person, token); // Assume this API call returns the updated user
            setAppUser(updatedUser);

            if (isCompany)
            {
                navigate('/onboarding/company-registration');
            }
            else
            {
                navigate('/landing');
            }
        }
        catch (error)
        {
            console.error('Registration failed', error);
        }
    };

    return (
        <div>
            <h1>Individual Registration</h1>

            <Label htmlFor={'firstName'}>First Name</Label>
            <Input value={firstName}
                   onChange={(_e, newValue) => setFirstName(newValue.value || '')}/>

            <Label htmlFor={'lastName'}>Last Name</Label>
            <Input value={lastName} onChange={(_e, newValue) => setLastName(newValue.value || '')}/>

            <Label htmlFor={'identificationNumber'}>Identification Number</Label>
            <Input value={idNumber}
                   onChange={(_e, newValue) => setIdNumber(newValue.value || '')}/>

            <Label htmlFor="idType">ID Type</Label>
            <Dropdown placeholder="Select an animal" id="idType">
                {idTypes.map((option) => (
                    <Option key={option.key} value={idType}>
                        {option.text}
                    </Option>
                ))}
            </Dropdown>

            <Checkbox label="I'm a company" checked={isCompany} onChange={(_e, checked) => setIsCompany(!!checked)}/>

            <Button onClick={onRegisterIndividual}> Register </Button>
        </div>
    );
};

export default IndividualRegistration;