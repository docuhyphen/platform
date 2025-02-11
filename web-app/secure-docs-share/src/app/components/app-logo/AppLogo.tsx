import React from 'react';
import './AppLogo.css';

const AppLogo: React.FC = () =>
{
    return (
        <span id="app-logo">
            <span>
                <span id="logo-doc">DOC</span>
                <span id="logo-hyphen"></span>
            </span>
            <span>HYPHEN</span>
        </span>
    );
};

export default AppLogo;