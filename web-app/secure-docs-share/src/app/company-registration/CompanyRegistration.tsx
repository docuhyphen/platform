import React, {useState} from 'react';
import './CompanyRegistration.css';
import {Button, Input, Label} from "@fluentui/react-components";


const CompanyRegistration: React.FC = () => {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [registrationNumber, setRegistrationNumber] = useState('');

  const handleRegister = () => {
    // Implement registration logic here
  };

  return (
      <div>
          <h1>Company Registration</h1>
          <Label htmlFor="firstName">First Name</Label>
          <Input id="firstName" value={firstName} onChange={(_e, newValue) => setFirstName(newValue.value || '')}/>

          <Label htmlFor="lastName">Last Name</Label>

          <Input id="lastName" value={lastName} onChange={(_e, newValue) => setLastName(newValue.value || '')}/>

          <Label htmlFor="companyName">Company Name</Label>

          <Input value={companyName}
                 onChange={(_e, newValue) => setCompanyName(newValue.value || '')}/>

          <Label htmlFor="companyRegistrationNumber">Registration Number</Label>
          <Input value={registrationNumber}
                 onChange={(_e, newValue) => setRegistrationNumber(newValue.value || '')}/>

          <Button onClick={handleRegister}> Register </Button>
      </div>
  );
};

export default CompanyRegistration;