import React from 'react';
import {Input, Label} from "@fluentui/react-components";

const Landing: React.FC = () =>
{
    return (
        <div>
            <h1>Profile</h1>
            <Label>Contact Details</Label>
            <Input type={"text"} placeholder={"Cell number"}/>
        </div>
    );
};

export default Landing;