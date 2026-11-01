import {useState} from "react";
import {Button, Divider, Text} from "@fluentui/react-components";
import {ContactDetailsDetailedDto, OrganizationDetailedDto} from "../../../models/models.tsx";
import {ProfileEditBasicDetailsIcon} from "../../../components/IconBundles.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../../components/phone-management/PhoneManagementDialog.tsx";
import EmailManagementDialog, {EmailManagementMode} from "../../../components/email-management/EmailManagementDialog.tsx";
import {useOrganizationContactDetailsStyles} from "./OrganizationContactDetailsStyles.tsx";

interface OrganizationContactDetailsProps
{
    organization: OrganizationDetailedDto;
    canManageOrganization: boolean;
    onContactDetailsUpdate: (contactDetails: ContactDetailsDetailedDto) => void;
}

const OrganizationContactDetails = ({organization, canManageOrganization, onContactDetailsUpdate}: OrganizationContactDetailsProps) =>
{
    const styles = useOrganizationContactDetailsStyles();
    const [isPhoneDialogOpen, setIsPhoneDialogOpen] = useState(false);
    const [isEmailDialogOpen, setIsEmailDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [emailManagementMode, setEmailManagementMode] = useState(EmailManagementMode.ADD);

    const onAddOrEditPhone = () =>
    {
        setPhoneManagementMode(organization.contactDetails?.phoneNumber ? PhoneManagementMode.EDIT : PhoneManagementMode.ADD);
        setIsPhoneDialogOpen(true);
    };

    const onAddOrEditEmail = () =>
    {
        setEmailManagementMode(organization.contactDetails?.email ? EmailManagementMode.EDIT : EmailManagementMode.ADD);
        setIsEmailDialogOpen(true);
    };

    return (
        <>
            <Divider id={"organization-details-tab-divider-1"}
                     alignContent="start"
                     appearance="brand"
                     className={styles.mainDivider}>
                Contact Details
            </Divider>

            <div id={"organization-details-tab-div-10"}>
                <Text id={"organization-details-tab-text-9"}
                      size={500}
                      className={styles.dataEditable}>
                    {canManageOrganization && (
                        <Button
                            id={"button-edit-org-email"}
                            appearance="subtle"
                            shape={"circular"}
                            size="small"
                            icon={<ProfileEditBasicDetailsIcon/>}
                            onClick={onAddOrEditEmail}
                        />
                    )}
                    {organization.contactDetails?.email || 'No email added'}
                </Text>
            </div>

            <div id={"organization-details-tab-div-11"}>
                <Text id={"organization-details-tab-text-10"}
                      size={500}
                      className={styles.dataEditable}>
                    {organization.contactDetails?.phoneNumber ? (
                        <>
                            {canManageOrganization && (
                                <Button
                                    id={"button-edit-org-phone"}
                                    appearance="subtle"
                                    shape={"circular"}
                                    size="small"
                                    icon={<ProfileEditBasicDetailsIcon/>}
                                    onClick={onAddOrEditPhone}
                                />
                            )}
                            {organization.contactDetails.phoneNumber}
                        </>
                    ) : (
                        canManageOrganization ? (
                            <Button
                                id={"button-add-org-phone"}
                                appearance="secondary"
                                shape="circular"
                                size="small"
                                onClick={onAddOrEditPhone}
                            >
                                Add phone number
                            </Button>
                        ) : (
                            <Text id={"organization-details-tab-text-11"}
                                  size={300}>No phone number added</Text>
                        )
                    )}
                </Text>
            </div>

            {canManageOrganization && (
                <>
                    <PhoneManagementDialog
                        isOpen={isPhoneDialogOpen}
                        mode={phoneManagementMode}
                        onDismiss={() => setIsPhoneDialogOpen(false)}
                        contactDetails={organization.contactDetails}
                        onComplete={onContactDetailsUpdate}
                    />

                    <EmailManagementDialog
                        isOpen={isEmailDialogOpen}
                        mode={emailManagementMode}
                        onDismiss={() => setIsEmailDialogOpen(false)}
                        contactDetails={organization.contactDetails}
                        onComplete={onContactDetailsUpdate}
                    />
                </>
            )}
        </>
    );
};

export default OrganizationContactDetails;
