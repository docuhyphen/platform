import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {OrganizationDetailedDto} from "../../../models/models.tsx";
import {useOrganizationPendingVerificationStyles} from "./OrganizationPendingVerificationStyles.tsx";

interface OrganizationPendingVerificationProps
{
    organization: OrganizationDetailedDto;
}

const OrganizationPendingVerification = ({organization}: OrganizationPendingVerificationProps) =>
{
    const styles = useOrganizationPendingVerificationStyles();

    return (
        <section id={"organization-details-tab-section-1"}
                 className={styles.orgOnboardingContainer}>
            <MessageBar id={"organization-details-tab-message-bar-1"}
                        intent={"warning"}>
                <MessageBarBody id={"organization-details-tab-message-bar-body-1"}>
                    <Text id={"organization-details-tab-text-1"}
                          weight="semibold">Organization Pending Verification</Text>
                    <br id={"organization-details-tab-br-1"}/>
                    Your organization <Text id={"organization-details-tab-text-2"}
                                            weight="semibold">{organization.name}</Text> has been registered
                    and is currently under review. We are verifying your details and you will receive a
                    confirmation email once the process is complete. You can check back here at any time for
                    status updates.
                </MessageBarBody>
            </MessageBar>
            <div id={"organization-details-tab-div-3"}
                 className={styles.pendingInfoRow}>
                <Text id={"organization-details-tab-text-3"}
                      size={300}>
                    Registration Number: {organization.registrationNumber}
                </Text>
            </div>
            <div id={"organization-details-tab-div-4"}
                 className={styles.pendingInfoRow}>
                <Text id={"organization-details-tab-text-4"}
                      size={300}>
                    Contact Email: {organization.contactDetails?.email || 'Not provided'}
                </Text>
            </div>
            <div id={"organization-details-tab-div-5"}>
                <Text id={"organization-details-tab-text-5"}
                      size={300}>
                    Contact Phone: {organization.contactDetails?.phoneNumber || 'Not provided'}
                </Text>
            </div>
            <div id={"organization-details-tab-div-6"}
                 className={styles.pendingInfoRow}>
                <Text id={"organization-details-tab-text-6"}
                      size={300}>
                    If any of these details are incorrect, please contact sales at{' '}
                    <a id={"organization-details-tab-a-1"}
                       href="mailto:sales@docuhyphen.com">sales@docuhyphen.com</a> so we can update your
                    registration review.
                </Text>
            </div>
        </section>
    );
};

export default OrganizationPendingVerification;
