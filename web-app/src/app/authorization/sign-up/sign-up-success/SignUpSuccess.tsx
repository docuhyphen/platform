import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useSignUpStyles} from "../SignUpStyles.tsx";

const SignUpSuccess = () =>
{
    const navigate = useNavigate();
    const signUpStyles = useSignUpStyles();

    return (
        <div id={"sign-up-div-6"}
             className={signUpStyles.signUpSuccessfulSection}>
            <Text id={"sign-up-text-3"}
                  align={"center"}
                  size={500}
                  font="monospace">
                Sign up successful!
            </Text>
            <Text id={"sign-up-text-4"}
                  align={"center"}
                  size={300}>
                Welcome aboard, your account has been created successfully.
            </Text>
            <Text id={"sign-up-text-5"}
                  align={"center"}
                  italic>
                <strong id={"sign-up-strong-2"}> Your account is protected </strong>.
                Two-factor authentication (2FA) is enabled by default to enhance
                your account security. We also recommend keeping your password
                secure with a trusted password manager.
            </Text>
            <Button
                id={"sign-up-success-sign-in-btn"}
                onClick={() => navigate("/sign-in")}
                appearance={"primary"}
                shape={"circular"}>
                Sign In
            </Button>
        </div>
    );
};

export default SignUpSuccess;
