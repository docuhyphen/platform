import React from 'react';
import logo from "../../../assets/logo.svg";
import {useAppLogoStyles} from "./AppLogoStyles.tsx";

const AppLogo: React.FC = () =>
{
    const styles = useAppLogoStyles();

    return (
        <span className={styles.appLogo}>
            <img src={logo} alt="Doc Hyphen Logo"/>
        </span>
    );
};

export default AppLogo;