import React from 'react';
import logo from "../../assets/logo.svg";
import {useAppLogoStyles} from "./AppLogoStyles.tsx";

const AppLogo: React.FC = () =>
{
    const styles = useAppLogoStyles();

    return (
        <span>
            <img src={logo} alt="DocuHyphen Logo" className={styles.appLogo}/>
        </span>
    );
};

export default AppLogo;