import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useSignUpStyles} from "../SignUpStyles.tsx";

const EmailConfirmSuccess = () =>
{
    const navigate = useNavigate();
    const signUpStyles = useSignUpStyles();

    return <div
        id={"email-confirm-success"}
        className={signUpStyles.signUpSuccessfulSection}>
        <Text
            id={"email-confirm-success-title"}
            align={"center"}
            size={500}
            font="monospace">
            Sign up successful!
        </Text>
        <Text
            id={"email-confirm-success-message"}
            align={"center"}
            size={300}>
            Welcome aboard, your account has been created successfully.
        </Text>
        <Text
            id={"email-confirm-success-security-note"}
            align={"center"}
            italic>
            <strong> Your account is protected </strong>.
            Two-factor authentication (2FA) is enabled by default to enhance
            your account security. We also recommend keeping your password
            secure with a trusted password manager.
        </Text>
        <Button
            id={"email-confirm-sign-in-btn"}
            onClick={() => navigate("/sign-in")}
            appearance={"primary"}
            shape={"circular"}>
            Sign In
        </Button>
    </div>;
};

export default EmailConfirmSuccess;
