import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {OrganizationDetailedDto} from "../../../models/models.tsx";
import {useOrganizationPendingReviewStyles} from "./OrganizationPendingReviewStyles.tsx";

interface OrganizationPendingReviewProps
{
    organization: OrganizationDetailedDto;
}

const OrganizationPendingReview = ({organization}: OrganizationPendingReviewProps) =>
{
    const styles = useOrganizationPendingReviewStyles();

    return (
        <section id={"organization-tab-section-1"}
                 className={styles.orgOnboardingContainer}>
            <MessageBar id={"organization-tab-message-bar-1"}
                        intent={"warning"}>
                <MessageBarBody id={"organization-tab-message-bar-body-1"}>
                    <Text id={"organization-tab-text-1"}
                          weight="semibold">Organization Pending Verification</Text>
                    <br id={"organization-tab-br-1"}/>
                </MessageBarBody>
            </MessageBar>
            <div id={"organization-tab-div-3"}
                 className={styles.onboardingRow}>
                <Text id={"organization-tab-text-2"}>
                    Your organization <Text id={"organization-tab-text-3"}
                                            weight="semibold">{organization.name}</Text> has been registered
                    and is currently under review. We are verifying your details and you will receive a
                    confirmation email once the process is complete. You can check back here at any time for
                    status updates.
                </Text>
            </div>
            <div id={"organization-tab-div-4"}
                 className={styles.onboardingRow}>
                <Text id={"organization-tab-text-4"}
                      size={300}>
                    Registration Number: {organization.registrationNumber}
                </Text>
            </div>
            <div id={"organization-tab-div-5"}
                 className={styles.onboardingRow}>
                <Text id={"organization-tab-text-5"}
                      size={300}>
                    Contact Email: {organization.contactDetails?.email || 'Not provided'}
                </Text>
            </div>
            <div id={"organization-tab-div-6"}>
                <Text id={"organization-tab-text-6"}
                      size={300}>
                    Contact Phone: {organization.contactDetails?.phoneNumber || 'Not provided'}
                </Text>
            </div>
            <div id={"organization-tab-div-7"}
                 className={styles.onboardingRow}>
                <Text id={"organization-tab-text-7"}
                      size={300}>
                    If any of these details are incorrect, please contact sales at{' '}
                    <a id={"organization-tab-a-1"}
                       href="mailto:sales@docuhyphen.com">sales@docuhyphen.com</a> so we can update your
                    registration review.
                </Text>
            </div>
        </section>
    );
};

export default OrganizationPendingReview;
