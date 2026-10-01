import {Spinner, Subtitle1} from "@fluentui/react-components";
import {useSignUpStyles} from "../SignUpStyles.tsx";
import {useAuthorizationStyles} from "../../AuthorizationStyles.tsx";

const EmailConfirmValidating = () =>
{
    const signUpStyles = useSignUpStyles();
    const authorizationStyles = useAuthorizationStyles();

    return <div
        id={"email-confirm-validating"}
        className={authorizationStyles.authorizationFormSection}>
        <Subtitle1
            id={"email-confirm-validating-title"}
            align={"center"}>
            Checking your verification link
        </Subtitle1>
        <div
            id={"email-confirm-validating-spinner-container"}
            className={signUpStyles.validatingSpinnerContainer}>
            <Spinner
                id={"email-confirm-validating-spinner"}
                size={"medium"}
                label={"Validating..."}
            />
        </div>
    </div>;
};

export default EmailConfirmValidating;
