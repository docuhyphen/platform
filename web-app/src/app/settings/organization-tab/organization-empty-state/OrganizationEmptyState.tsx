import {Text} from "@fluentui/react-components";
import OrganizationOnboardingForm from "../../../onboarding/organization-onboarding/OrganizationOnboardingForm.tsx";
import OrganizationEmptyStateIllustration
    from "../organization-empty-state-illustration/OrganizationEmptyStateIllustration.tsx";
import {useOrganizationEmptyStateStyles} from "./OrganizationEmptyStateStyles.tsx";

interface OrganizationEmptyStateProps
{
    onOrganizationRegistered: () => void;
}

const OrganizationEmptyState = ({onOrganizationRegistered}: OrganizationEmptyStateProps) =>
{
    const styles = useOrganizationEmptyStateStyles();

    return (
        <section
            id={"organization-empty-state"}
            className={styles.orgOnboardingContainer}>
            <OrganizationEmptyStateIllustration/>
            <Text
                id={"organization-empty-state-message"}
                align={"center"}>
                You are not part of an organization. You can onboard your organization to use the full potential of the platform.
            </Text>
            <div id={"organization-empty-state-action"}
                 className={styles.registrationForm}>
                <OrganizationOnboardingForm onOrganizationRegistered={onOrganizationRegistered}/>
            </div>
        </section>
    );
};

export default OrganizationEmptyState;
