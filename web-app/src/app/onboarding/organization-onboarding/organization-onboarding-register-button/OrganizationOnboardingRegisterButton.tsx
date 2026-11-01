import {Button, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface OrganizationOnboardingRegisterButtonProps
{
    registering: boolean;
    onClick: () => void;
}

const OrganizationOnboardingRegisterButton = ({registering, onClick}: OrganizationOnboardingRegisterButtonProps) =>
{
    const globalStyles = useGlobalStyles();

    return (
        <Button id={"org-onboarding-register-btn"}
                appearance={"primary"}
                shape={"circular"}
                className={globalStyles.buttonWithLoading}
                onClick={onClick}>
            {registering && <>
                <Spinner id={"organization-onboarding-form-spinner-1"}
                         size={"tiny"}/>
                Register
            </>
            }
            {!registering && "Register"}

        </Button>
    );
};

export default OrganizationOnboardingRegisterButton;
