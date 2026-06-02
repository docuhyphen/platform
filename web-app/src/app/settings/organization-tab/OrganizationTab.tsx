import React, {useEffect, useState} from "react";
import {Button, Divider, Spinner, Switch, Text, tokens} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {updateOrganizationSettings} from "../../../services/organizationApi";
import {ContactDetailsDetailedDto, OrganizationDetailedDto, OrganizationSettingsDto} from "../../models/models.tsx";
import {AppUserRole} from "../../models/models.tsx";
import {ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import EmailManagementDialog, {EmailManagementMode} from "../../components/email-management/EmailManagementDialog.tsx";
import {useOrganizationTabStyles} from "./OrganizationTabStyles.tsx";
import OrganizationDetailsEditDialog from "./details-edit-dialog/OrganizationDetailsEditDialog.tsx";
import {AxiosError} from "axios";
import OrganizationOnboardingDialog from "./organization-onboarding-dialog/OrganizationOnboardingDialog.tsx";
import {AuthSessionPolicySection} from "./AuthSessionPolicySection.tsx";

const OrganizationTab = () =>
{
    const styles = useOrganizationTabStyles()
    const {appUser, token} = useAuth();
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
    const [isOnboardingDialogOpen, setOnboardingDialogOpen] = useState(false)

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
        console.log("OrganizationTab mounted")
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

        {organization && !fetchingOrganization && (
            <div className={styles.container}>
                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>
                    Organization Details
                    <Button
                        icon={<ProfileEditBasicDetailsIcon/>}
                        onClick={() => setIsDetailsDialogOpen(true)}
                        appearance="subtle"
                    />
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
                    Organization Settings
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

                <Divider alignContent="start"
                         appearance="brand"
                         className={styles.mainDivider}>
                    Contact Details
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

                <OrganizationDetailsEditDialog
                    isOpen={isDetailsDialogOpen}
                    onDismiss={() => setIsDetailsDialogOpen(false)}
                    organization={organization}
                    onComplete={(updatedOrg) => setOrganization(updatedOrg)}
                />
            </div>
        )}

        {!organization && !fetchingOrganization && <>
            <section className={styles.orgOnboardingContainer}>
                <Text>
                    You are not part of an organization. You can onboard your organization to use the full
                    potential of DocHyphen.
                </Text>
                <div>
                    <Button shape={"circular"}
                            appearance={"outline"}
                            onClick={() => setOnboardingDialogOpen(true)}
                            icon={<></>}>
                        Register your organization
                    </Button>
                </div>
            </section>
            <OrganizationOnboardingDialog isOpen={isOnboardingDialogOpen}
                                          onDismiss={() => {
                                              setOnboardingDialogOpen(false);
                                              getOrganization();
                                          }}
                                          onRegistered={() => getOrganization()}/>
        </>
        }
    </>
}

export default OrganizationTab;