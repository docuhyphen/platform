import React from 'react';
import './AppLogo.css';
import logo from "../../../assets/logo.svg";

const AppLogo: React.FC = () =>
{
    return (
        <span id="app-logo">
            <img src={logo} alt="Doc Hyphen Logo"/>
        </span>
    );
};

export default AppLogo;