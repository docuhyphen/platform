import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import AppLogo from "../../../components/app-logo/AppLogo.tsx";
import AccountRecoveryCarousel from "../../carousel/AccountRecoveryCarousel.tsx";
import {useAuthorizationStyles} from "../../AuthorizationStyles.tsx";

const AccountRecoverySuccess = () =>
{
    const navigate = useNavigate();
    const authorizationStyles = useAuthorizationStyles();

    return (
        <section id={"account-recovery-section-1"}
                 className={authorizationStyles.auth}>
            <section id={"account-recovery-section-2"}
                     className={authorizationStyles.authSection}>
                <section id={"account-recovery-section-3"}
                         className={authorizationStyles.authSection1}>
                    <div id={"account-recovery-div-1"}>
                        <AppLogo/>
                    </div>
                    <div id={"account-recovery-div-2"}
                         className={authorizationStyles.authorizationFormSection}>
                        <Text id={"account-recovery-text-2"}
                              align={"center"}
                              size={500}
                              font="monospace">
                            Password Reset Successful!
                        </Text>
                        <Text id={"account-recovery-text-3"}
                              align={"center"}
                              size={300}>
                            Your password has been updated successfully.
                        </Text>
                        <Button
                            id={"account-recovery-success-sign-in-btn"}
                            onClick={() => navigate("/sign-in")}
                            appearance={"primary"}
                            shape={"circular"}>
                            Sign In
                        </Button>
                    </div>
                </section>
                <section id={"account-recovery-section-4"}
                         className={authorizationStyles.authSection2}>
                    <AccountRecoveryCarousel/>
                </section>
            </section>
        </section>
    );
};

export default AccountRecoverySuccess;
