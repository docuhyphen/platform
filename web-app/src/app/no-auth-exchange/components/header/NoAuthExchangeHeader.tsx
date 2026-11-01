import React from "react";
import AppLogo from "../../../components/app-logo/AppLogo.tsx";
import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useNoAuthExchangeHeaderStyles} from "./NoAuthExchangeHeaderStyles.tsx";

const NoAuthExchangeHeader: React.FC = () =>
{
    const navigate = useNavigate();
    const styles = useNoAuthExchangeHeaderStyles();

    return (
        <header
            id={"no-auth-exchange-header"}
            className={styles.container}
        >
            <div id={"no-auth-exchange-header-div-1"}
                 className={styles.brandContainer}>
                <AppLogo/>
                <Text id={"no-auth-exchange-header-text-1"}
                      className={styles.secureRequestLabel}>Secure Exchange</Text>
            </div>
            <div id={"no-auth-exchange-header-div-2"}
                 className={styles.signInButtonContainer}>
                <Text id={"no-auth-exchange-header-text-2"}
                      className={styles.signInPrompt}>Already have an account?</Text>
                <Button
                    id={"no-auth-exchange-header-sign-in-btn"}
                    onClick={() => navigate('/sign-in')}
                    className={styles.signInButton}
                    appearance={"primary"}
                    shape={"circular"}
                >
                    Sign In
                </Button>
            </div>
        </header>
    );
};

export default NoAuthExchangeHeader;
