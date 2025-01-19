import React, { useState } from 'react';
import { TextField, PrimaryButton, Dropdown, IDropdownOption } from '@fluentui/react';
import './IndividualRegistration.css';

const idTypes: IDropdownOption[] = [
  { key: 'ID_NUMBER', text: 'ID Number' },
  { key: 'PASSPORT_NUMBER', text: 'Passport Number' },
  { key: 'SOCIAL_SECURITY', text: 'Social Security' },
];

const IndividualRegistration: React.FC = () => {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const [idType, setIdType] = useState(idTypes[0].key as string);

  const handleRegister = () => {
    // Implement registration logic here
  };

  return (
    <div>
      <h1>Individual Registration</h1>
      <TextField label="First Name" value={firstName} onChange={(e, newValue) => setFirstName(newValue || '')} />
      <TextField label="Last Name" value={lastName} onChange={(e, newValue) => setLastName(newValue || '')} />
      <TextField label="Identification Number" value={idNumber} onChange={(e, newValue) => setIdNumber(newValue || '')} />
      <Dropdown label="ID Type" selectedKey={idType} options={idTypes} onChange={(e, option) => setIdType(option?.key as string)} />
      <PrimaryButton text="Register" onClick={handleRegister} />
    </div>
  );
};

export default IndividualRegistration;