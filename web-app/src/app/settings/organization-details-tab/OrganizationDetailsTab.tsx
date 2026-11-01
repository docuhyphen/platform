import {useState} from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext";
import {Capability} from '../../models/models.tsx';
import {useOrganizationDetailsTabStyles} from "./OrganizationDetailsTabStyles.tsx";
import {useOrganizationDetails} from "./useOrganizationDetails.ts";
import OrganizationDetailsEditDialog from "./details-edit-dialog/OrganizationDetailsEditDialog.tsx";
import OrganizationPendingVerification
    from "./organization-pending-verification/OrganizationPendingVerification.tsx";
import OrganizationContactDetails from "./organization-contact-details/OrganizationContactDetails.tsx";
import OrganizationPreferences from "./organization-preferences/OrganizationPreferences.tsx";

const OrganizationDetailsTab = () =>
{
    const styles = useOrganizationDetailsTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const details = useOrganizationDetails();
    const {organization, fetchingOrganization, error} = details;
    const [isDetailsDialogOpen, setIsDetailsDialogOpen] = useState(false);
    const canManageOrganization = !!(appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE));

    return <>
        {fetchingOrganization &&
            <div id={"organization-details-tab-div-1"}
                 className={styles.loadingWrapper}>
                <Spinner
                    id={"organization-details-tab-spinner-1"}
                    label="Loading"
                    size={"small"}
                />
            </div>
        }

        {error && (
            <div id={"organization-details-tab-div-2"}
                 className={styles.errorMessage}>
                {error}
            </div>
        )}

        {organization && !fetchingOrganization && !organization.isActive && (
            <OrganizationPendingVerification organization={organization}/>
        )}

        {organization && !fetchingOrganization && organization.isActive && (
            <div id={"organization-details-tab-div-7"}
                 className={styles.container}>

                <div id={"organization-details-tab-div-8"}
                     className={styles.orgNameSection}>
                    <Text
                        id={"organization-details-tab-text-7"}
                        size={500}
                        weight="semibold"
                    >
                        {organization.name || "Unnamed Organization"}
                    </Text>
                    {organization.registrationNumber && (
                        <div id={"organization-details-tab-div-9"}
                             className={styles.registrationRow}>
                            <Text id={"organization-details-tab-text-8"}
                                  size={300}>Registration: {organization.registrationNumber}</Text>
                        </div>
                    )}
                </div>

                <OrganizationContactDetails organization={organization}
                                            canManageOrganization={canManageOrganization}
                                            onContactDetailsUpdate={details.handleContactDetailsUpdate}/>

                <OrganizationPreferences settings={details.organizationSettings}
                                         canManageOrganization={canManageOrganization}
                                         saving={details.savingSettings}
                                         onSettingChange={details.handleSettingChange}/>

                {canManageOrganization && (
                    <OrganizationDetailsEditDialog
                        isOpen={isDetailsDialogOpen}
                        onDismiss={() => setIsDetailsDialogOpen(false)}
                        organization={organization}
                        onComplete={(updatedOrg) => details.setOrganization(updatedOrg)}
                    />
                )}
            </div>
        )}
    </>
}

export default OrganizationDetailsTab;
