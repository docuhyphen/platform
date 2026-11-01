import React from 'react';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import AccountRecoveryCarousel from "../carousel/AccountRecoveryCarousel.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useAccountRecovery} from "./useAccountRecovery.ts";
import AccountRecoveryForm from "./account-recovery-form/AccountRecoveryForm.tsx";
import AccountRecoverySuccess from "./account-recovery-success/AccountRecoverySuccess.tsx";

const AccountRecovery: React.FC = () =>
{
    const authorizationStyles = useAuthorizationStyles();
    const recovery = useAccountRecovery();

    if (recovery.pwdResetSuccessful)
    {
        return <AccountRecoverySuccess/>;
    }

    return (
        <section id={"account-recovery-section-5"}
                 className={authorizationStyles.auth}>
            <section id={"account-recovery-section-6"}
                     className={authorizationStyles.authSection}>
                <section id={"account-recovery-section-7"}
                         className={authorizationStyles.authSection1}>
                    <div id={"account-recovery-div-3"}>
                        <AppLogo/>
                    </div>
                    <AccountRecoveryForm recovery={recovery}/>
                </section>
                <section id={"account-recovery-section-8"}
                         className={authorizationStyles.authSection2}>
                    <AccountRecoveryCarousel/>
                </section>
            </section>
        </section>
    );
};

export default AccountRecovery;
