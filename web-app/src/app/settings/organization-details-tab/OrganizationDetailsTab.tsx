import React, {useCallback, useEffect, useState} from "react";
import {
    Button,
    Divider,
    MessageBar,
    MessageBarBody,
    Spinner,
    Switch,
    Text,
} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {updateOrganizationSettings} from "../../../services/organizationApi";
import {ContactDetailsDetailedDto, OrganizationDetailedDto, OrganizationSettingsDto} from "../../models/models.tsx";
import {Capability} from '../../models/models.tsx';
import {
    ProfileEditBasicDetailsIcon
} from "../../components/IconBundles.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import EmailManagementDialog, {EmailManagementMode} from "../../components/email-management/EmailManagementDialog.tsx";
import {useOrganizationTabStyles} from "./OrganizationDetailsTabStyles.tsx";
import OrganizationDetailsEditDialog from "./details-edit-dialog/OrganizationDetailsEditDialog.tsx";
import {AxiosError} from "axios";

const OrganizationDetailsTab = () =>
{

    const styles = useOrganizationTabStyles()
    const {appUser, token, appUserPersonOrganization, hasCapability} = useAuth();
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
    const canManageOrganization = appUserPersonOrganization?.isActive &&
        (hasCapability(Capability.APP_ADMIN) || hasCapability(Capability.ORG_POLICY_MANAGE));

    const getOrganization = useCallback(async () =>
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
        catch (err: unknown)
        {
            if (!(err instanceof AxiosError))
            {
                const message = err instanceof Error ? err.message : "Failed to fetch organization";
                setError(message);
                console.error("Failed to fetch organization:", err);
            }
        }
        finally
        {
            setFetchingOrganization(false);
        }
    }, [appUser?.id, appUser?.person?.id, token]);

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
        catch (err: unknown)
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
    }, [getOrganization]);

    return <>
        {fetchingOrganization &&
            <div className={styles.loadingWrapper}>
                <Spinner
                    label="Loading"
                    size={"small"}
                />
            </div>
        }

        {error && (
            <div className={styles.errorMessage}>
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
                <div className={styles.pendingInfoRow}>
                    <Text size={300}>
                        Registration Number: {organization.registrationNumber}
                    </Text>
                </div>
                <div className={styles.pendingInfoRow}>
                    <Text size={300}>
                        Contact Email: {organization.contactDetails?.email || 'Not provided'}
                    </Text>
                </div>
                <div>
                    <Text size={300}>
                        Contact Phone: {organization.contactDetails?.phoneNumber || 'Not provided'}
                    </Text>
                </div>
                <div className={styles.pendingInfoRow}>
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

                <div className={styles.orgNameSection}>
                    <Text
                        size={500}
                        weight="semibold"
                    >
                        {organization.name || "Unnamed Organization"}
                    </Text>
                    {organization.registrationNumber && (
                        <div className={styles.registrationRow}>
                            <Text size={300}>Registration: {organization.registrationNumber}</Text>
                        </div>
                    )}
                </div>


                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>
                    Contact Details
                </Divider>

                <div>
                    <Text size={500} className={styles.dataEditable}>
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
                        {organization?.contactDetails?.email || 'No email added'}
                    </Text>
                </div>

                <div>
                    <Text size={500} className={styles.dataEditable}>
                        {organization?.contactDetails?.phoneNumber ? (
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
                                <Text size={300}>No phone number added</Text>
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
                    </>
                )}

                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>
                    Organization Preferences
                </Divider>

                {organizationSettings && canManageOrganization && <>
                    <Switch
                        id={"switch-discoverable-for-trust-requests"}
                        checked={organizationSettings.discoverableForTrustRequests}
                        onChange={(_, data) => handleSettingChange('discoverableForTrustRequests', data.checked)}
                        label="Allow verified organizations to find us for trust requests"
                        disabled={savingSettings}
                    />
                    <Switch
                        id={"switch-allow-external-customer-sharing"}
                        checked={organizationSettings.allowExternalCustomerSharing !== false}
                        onChange={(_, data) => handleSettingChange('allowExternalCustomerSharing', data.checked)}
                        label="Allow sharing with external customers (individuals)"
                        disabled={savingSettings}
                    />
                    <Switch
                        id={"switch-require-trusted-organization-for-b2b"}
                        checked={organizationSettings.requireTrustedOrganizationForB2b}
                        onChange={(_, data) => handleSettingChange('requireTrustedOrganizationForB2b', data.checked)}
                        label="Require a trusted organization for sharing with other organizations"
                        disabled={savingSettings}
                    />
                    <Switch
                        id={"switch-allow-profile-update"}
                        checked={organizationSettings.allowProfileUpdate}
                        onChange={(_, data) => handleSettingChange('allowProfileUpdate', data.checked)}
                        label="Allow users to update their basic profiles"
                        disabled={savingSettings}
                    />
                    <Switch
                        id={"switch-allow-email-update"}
                        checked={organizationSettings.allowEmailUpdate}
                        onChange={(_, data) => handleSettingChange('allowEmailUpdate', data.checked)}
                        label="Allow users to update their email addresses"
                        disabled={savingSettings}
                    />
                </>}

                {organizationSettings && !canManageOrganization && (
                    <div id={"organization-preferences-summary"}>
                        <Text size={300}>Trust request discovery: {organizationSettings.discoverableForTrustRequests ? 'Enabled' : 'Disabled'}</Text><br/>
                        <Text size={300}>External sharing: {organizationSettings.allowExternalCustomerSharing !== false ? 'Enabled' : 'Disabled'}</Text><br/>
                        <Text size={300}>Trusted organization required for B2B sharing: {organizationSettings.requireTrustedOrganizationForB2b ? 'Enabled' : 'Disabled'}</Text><br/>
                        <Text size={300}>Profile updates: {organizationSettings.allowProfileUpdate ? 'Allowed' : 'Not allowed'}</Text><br/>
                        <Text size={300}>Email updates: {organizationSettings.allowEmailUpdate ? 'Allowed' : 'Not allowed'}</Text>
                    </div>
                )}


                {canManageOrganization && (
                    <OrganizationDetailsEditDialog
                        isOpen={isDetailsDialogOpen}
                        onDismiss={() => setIsDetailsDialogOpen(false)}
                        organization={organization}
                        onComplete={(updatedOrg) => setOrganization(updatedOrg)}
                    />
                )}
            </div>
        )}
    </>
}

export default OrganizationDetailsTab;
