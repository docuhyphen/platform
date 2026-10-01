import {Button, MessageBar, MessageBarBody, Subtitle1, Text} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import {useNavigate} from "react-router-dom";
import {useAuthorizationStyles} from "../../AuthorizationStyles.tsx";

interface EmailConfirmInvalidLinkProps
{
    tokenError?: string;
}

const EmailConfirmInvalidLink = ({tokenError}: EmailConfirmInvalidLinkProps) =>
{
    const navigate = useNavigate();
    const authorizationStyles = useAuthorizationStyles();

    return <div
        id={"email-confirm-invalid-link"}
        className={authorizationStyles.authorizationFormSection}>
        <Subtitle1
            id={"email-confirm-invalid-title"}
            align={"center"}>
            <Button
                id={"email-confirm-invalid-back-btn"}
                shape={"circular"}
                icon={<ArrowLeftRegular/>}
                appearance={"transparent"}
                onClick={() => navigate("/sign-up")}
            />
            Verification link issue
        </Subtitle1>
        <MessageBar
            id={"email-confirm-invalid-message"}
            intent={"warning"}>
            <MessageBarBody id={"email-confirm-invalid-message-body"}>
                <Text size={200}>
                    {tokenError || "This verification link is invalid or has expired."}
                    {" "}Please start sign-up again to receive a new email.
                </Text>
            </MessageBarBody>
        </MessageBar>
        <Button
            id={"email-confirm-back-to-signup-btn"}
            onClick={() => navigate("/sign-up")}
            appearance={"primary"}
            shape={"circular"}>
            Back to sign up
        </Button>
    </div>;
};

export default EmailConfirmInvalidLink;
