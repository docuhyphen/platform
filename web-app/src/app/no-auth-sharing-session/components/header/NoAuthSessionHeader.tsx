import React, {useState} from "react";
import AppLogo from "../../../components/app-logo/AppLogo.tsx";
import {Button, Spinner} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useNoAuthSessionHeaderStyles} from "./NoAuthSessionHeaderStyles.tsx";

const NoAuthSessionHeader: React.FC = () =>
{
    const navigate = useNavigate();
    const styles = useNoAuthSessionHeaderStyles();
    const [signingIn, setSigningIn] = useState(false);

    const onSignIn = () =>
    {
        setSigningIn(true);

        setTimeout(() =>
        {
            setSigningIn(false);
            navigate('/sign-in');
        }, 3000)
    }

    return (
        <section className={styles.container}>
            <AppLogo/>
            <div className={styles.signInButtonContainer}>
                Already have an account?
                <Button onClick={onSignIn}
                        disabled={signingIn}
                        className={styles.signInButton}
                        appearance={"primary"}
                        shape={"circular"}>
                    {signingIn &&
                        <Spinner size={"tiny"}/>
                    }
                    Sign In
                </Button>
            </div>
        </section>
    );
}

export default NoAuthSessionHeader;