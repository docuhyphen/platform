import React, {useEffect, useState} from "react";
import {
    Button,
    Divider,
    MessageBar,
    MessageBarBody, SelectTabData, SelectTabEvent,
    Spinner,
    Switch,
    Tab, TabList, TabValue,
    Text,
    tokens
} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {updateOrganizationSettings} from "../../../services/organizationApi";
import {ContactDetailsDetailedDto, OrganizationDetailedDto, OrganizationSettingsDto} from "../../models/models.tsx";
import {AppUserRole} from "../../models/models.tsx";
import {
    ProfileEditBasicDetailsIcon
} from "../../components/IconBundles.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import EmailManagementDialog, {EmailManagementMode} from "../../components/email-management/EmailManagementDialog.tsx";
import {useOrganizationTabStyles} from "./OrganizationDetailsTabStyles.tsx";
import OrganizationDetailsEditDialog from "./details-edit-dialog/OrganizationDetailsEditDialog.tsx";
import {AxiosError} from "axios";
import OrganizationOnboardingDialog from "./organization-onboarding-dialog/OrganizationOnboardingDialog.tsx";
import {AuthSessionPolicySection} from "./AuthSessionPolicySection.tsx";

const OrganizationDetailsTab = () =>
{

    const styles = useOrganizationTabStyles()
    const {appUser, token, appUserPersonOrganization} = useAuth();
    const [organization, setOrganization] = useState<OrganizationDetailedDto | null>(null);
    const [fetchingOrganization, setFetchingOrganization] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isDetailsDialogOpen, setIsDetailsDialogOpen] = useState(false);
    const [savingSettings, setSavingSettings] = useState(false);
    const [organizationSettings, setOrganizationSettings] = useState<OrganizationSettingsDto | null>(null);
    const [isPhoneDialogOpen, setIsPhoneDialogOpen] = useState(false);
    const [isEmailDialogOpen, setIsEmailDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [emailManagementMode, setEmailManagementMode] = useState(EmailManagementMode.ADD);
    const [isOnboardingDialogOpen, setOnboardingDialogOpen] = useState(false);
    const roleValue = `${appUser?.role ?? ''}`;

    const getOrganization = async () =>
    {
        if (!appUser?.id || !appUser?.person?.id) return;

        setFetchingOrganization(true);
        setError(null);

        try
        {
            const organization = await fetchAppUserPersonOrganization(appUser.id, appUser.person.id, token || undefined);
            setOrganization(organization);
            setOrganizationSettings(organization.settings);
        }
        catch (err: any)
        {
            if (err instanceof AxiosError)
            {

            }
            else
            {
                setError(err.message || "Failed to fetch organization");
                console.error("Failed to fetch organization:", err);
            }

        }
        finally
        {
            setFetchingOrganization(false);
        }
    };

    const handleSettingChange = async (setting: keyof OrganizationSettingsDto, value: boolean) =>
    {
        if (!organization?.id || !organizationSettings) return;

        setSavingSettings(true);
        setError(null);

        const updatedSettings = {
            ...organizationSettings,
            [setting]: value
        };

        try
        {
            await updateOrganizationSettings(
                organization.id,
                updatedSettings,
                token || undefined
            );
            setOrganizationSettings(updatedSettings);
            setOrganization({
                ...organization,
                settings: updatedSettings
            });
        }
        catch (err: any)
        {
            setError(err.message || "Failed to update organization settings");
            console.error("Failed to update organization settings:", err);
        }
        finally
        {
            setSavingSettings(false);
        }
    };

    const onAddOrEditPhone = () =>
    {
        if (organization?.contactDetails?.phoneNumber)
        {
            setPhoneManagementMode(PhoneManagementMode.EDIT);
        }
        else
        {
            setPhoneManagementMode(PhoneManagementMode.ADD);
        }
        setIsPhoneDialogOpen(true);
    };

    const onAddOrEditEmail = () =>
    {
        if (organization?.contactDetails?.email)
        {
            setEmailManagementMode(EmailManagementMode.EDIT);
        }
        else
        {
            setEmailManagementMode(EmailManagementMode.ADD);
        }
        setIsEmailDialogOpen(true);
    };

    const handleContactDetailsUpdate = (updatedContactDetails: ContactDetailsDetailedDto) =>
    {
        if (!organization) return;

        setOrganization({
            ...organization,
            contactDetails: updatedContactDetails
        });
    };

    useEffect(() =>
    {
        getOrganization()
    }, []);

    return <>
        {fetchingOrganization &&
            <div style={{display: 'flex', justifyContent: 'center', padding: '20px'}}>
                <Spinner label="Loading"
                         size={"small"}/>
            </div>
        }

        {error && (
            <div style={{color: tokens.colorStatusDangerForeground1, padding: '10px', marginBottom: '10px'}}>
                {error}
            </div>
        )}

        {organization && !fetchingOrganization && !organization.isActive && (
            <section className={styles.orgOnboardingContainer}>
                <MessageBar intent={"warning"}>
                    <MessageBarBody>
                        <Text weight="semibold">Organization Pending Verification</Text>
                        <br/>
                        Your organization <Text weight="semibold">{organization.name}</Text> has been registered
                        and is currently under review. We are verifying your details and you will receive a
                        confirmation email once the process is complete. You can check back here at any time for
                        status updates.
                    </MessageBarBody>
                </MessageBar>
                <div style={{marginTop: '8px'}}>
                    <Text size={300}>
                        Registration Number: {organization.registrationNumber}
                    </Text>
                </div>
                <div style={{marginTop: '8px'}}>
                    <Text size={300}>
                        Contact Email: {organization.contactDetails?.email || 'Not provided'}
                    </Text>
                </div>
                <div>
                    <Text size={300}>
                        Contact Phone: {organization.contactDetails?.phoneNumber || 'Not provided'}
                    </Text>
                </div>
                <div style={{marginTop: '8px'}}>
                    <Text size={300}>
                        If any of these details are incorrect, please contact sales at{' '}
                        <a href="mailto:sales@docuhyphen.com">sales@docuhyphen.com</a> so we can update your
                        registration review.
                    </Text>
                </div>
            </section>
        )}

        {organization && !fetchingOrganization && organization.isActive && (
            <div className={styles.container}>
                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>

                    Basic
                </Divider>

                <div style={{marginBottom: '20px'}}>
                    <Text size={500} weight="semibold">
                        {organization.name || "Unnamed Organization"}
                    </Text>
                    {organization.registrationNumber && (
                        <div style={{marginTop: '5px'}}>
                            <Text size={300}>Registration: {organization.registrationNumber}</Text>
                        </div>
                    )}
                </div>


                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>
                    Contact
                </Divider>

                <div>
                    <Text size={500} className={styles.dataEditable}>
                        <Button
                            appearance="subtle"
                            size="small"
                            icon={<ProfileEditBasicDetailsIcon/>}
                            onClick={onAddOrEditEmail}
                        />
                        {organization?.contactDetails?.email || 'No email added'}
                    </Text>
                </div>

                <div>
                    <Text size={500} className={styles.dataEditable}>
                        {organization?.contactDetails?.phoneNumber ? (
                            <>
                                <Button
                                    appearance="subtle"
                                    size="small"
                                    icon={<ProfileEditBasicDetailsIcon/>}
                                    onClick={onAddOrEditPhone}
                                />
                                {organization.contactDetails.phoneNumber}
                            </>
                        ) : (
                            <Button
                                appearance="outline"
                                shape="circular"
                                size="small"
                                onClick={onAddOrEditPhone}
                            >
                                Add phone number
                            </Button>
                        )}
                    </Text>
                </div>

                <PhoneManagementDialog
                    isOpen={isPhoneDialogOpen}
                    mode={phoneManagementMode}
                    onDismiss={() => setIsPhoneDialogOpen(false)}
                    contactDetails={organization?.contactDetails}
                    onComplete={handleContactDetailsUpdate}
                />

                <EmailManagementDialog
                    isOpen={isEmailDialogOpen}
                    mode={emailManagementMode}
                    onDismiss={() => setIsEmailDialogOpen(false)}
                    contactDetails={organization?.contactDetails}
                    onComplete={handleContactDetailsUpdate}
                />
                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>
                    Organization Preferences
                </Divider>

                {organizationSettings && <>
                    <Switch
                        checked={organizationSettings.allowExternalCustomerSharing !== false}
                        onChange={(_, data) => handleSettingChange('allowExternalCustomerSharing', data.checked)}
                        label="Allow sharing with external customers (individuals)"
                        disabled={savingSettings}
                    />
                    <Switch
                        checked={organizationSettings.allowShareWithoutPairing}
                        onChange={(_, data) => handleSettingChange('allowShareWithoutPairing', data.checked)}
                        label="Allow sharing with unpaired organizations"
                        disabled={savingSettings}
                    />
                    <Switch
                        checked={organizationSettings.allowProfileUpdate}
                        onChange={(_, data) => handleSettingChange('allowProfileUpdate', data.checked)}
                        label="Allow users to update their basic profiles"
                        disabled={savingSettings}
                    />
                    <Switch
                        checked={organizationSettings.allowEmailUpdate}
                        onChange={(_, data) => handleSettingChange('allowEmailUpdate', data.checked)}
                        label="Allow users to update their email addresses"
                        disabled={savingSettings}
                    />
                </>}

                {organization?.id && appUser?.role === AppUserRole.ORG_ADMIN && (
                    <AuthSessionPolicySection organizationId={organization.id}/>
                )}

                <OrganizationDetailsEditDialog
                    isOpen={isDetailsDialogOpen}
                    onDismiss={() => setIsDetailsDialogOpen(false)}
                    organization={organization}
                    onComplete={(updatedOrg) => setOrganization(updatedOrg)}
                />
            </div>
        )}
    </>
}

export default OrganizationDetailsTab;