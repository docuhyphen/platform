import React, {useEffect, useState} from "react";
import {Button, Divider, Field, Input, Spinner, Switch, Text} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {updateOrganization, updateOrganizationSettings} from "../../../services/organizationApi";
import {ContactDetailsDetailedDto, OrganizationDetailedDto, OrganizationSettingsDto} from "../../models/models.tsx";
import {ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import EmailManagementDialog, {EmailManagementMode} from "../../components/email-management/EmailManagementDialog.tsx";
import {useOrganizationTabStyles} from "./OrganizationTabStyles.tsx";

const OrganizationTab = () =>
{
    const styles = useOrganizationTabStyles()
    const {appUser, token} = useAuth();
    const [organization, setOrganization] = useState<OrganizationDetailedDto | null>(null);
    const [fetchingOrganization, setFetchingOrganization] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editMode, setEditMode] = useState(false);
    const [organizationName, setOrganizationName] = useState("");
    const [savingSettings, setSavingSettings] = useState(false);
    const [savingName, setSavingName] = useState(false);
    const [organizationSettings, setOrganizationSettings] = useState<OrganizationSettingsDto | null>(null);
    const [isPhoneDialogOpen, setIsPhoneDialogOpen] = useState(false);
    const [isEmailDialogOpen, setIsEmailDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [emailManagementMode, setEmailManagementMode] = useState(EmailManagementMode.ADD);

    const getOrganization = async () =>
    {
        if (!appUser?.id || !appUser?.person?.id) return;

        setFetchingOrganization(true);
        setError(null);

        try
        {
            const organization = await fetchAppUserPersonOrganization(appUser.id, appUser.person.id, token || undefined);
            setOrganization(organization);
            setOrganizationName(organization.name || "");
            setOrganizationSettings(organization.settings);
        }
        catch (err: any)
        {
            setError(err.message || "Failed to fetch organization");
            console.error("Failed to fetch organization:", err);
        }
        finally
        {
            setFetchingOrganization(false);
        }
    };

    const handleSaveOrgName = async () =>
    {
        if (!organization?.id) return;

        setSavingName(true);
        setError(null);

        try
        {
            await updateOrganization(
                organization.id,
                {name: organizationName},
                token || undefined
            );
            setOrganization({
                ...organization,
                name: organizationName
            });
            setEditMode(false);
        }
        catch (err: any)
        {
            setError(err.message || "Failed to update organization name");
            console.error("Failed to update organization name:", err);
        }
        finally
        {
            setSavingName(false);
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
                <Spinner label="Loading organization information..."/>
            </div>
        }

        {error && (
            <div style={{color: 'red', padding: '10px', marginBottom: '10px'}}>
                {error}
            </div>
        )}

        {organization && !fetchingOrganization && (
            <div className={styles.container}>
                <Divider alignContent="start" appearance="brand">
                    Organization Details
                    {!editMode && (
                        <Button
                            icon={<ProfileEditBasicDetailsIcon/>}
                            onClick={() => setEditMode(true)}
                            appearance="subtle"
                        />
                    )}
                </Divider>

                {editMode ? (
                    <div style={{marginBottom: '20px'}}>
                        <Field label="Organization Name">
                            <Input
                                value={organizationName}
                                onChange={(e) => setOrganizationName(e.target.value)}
                                disabled={savingName}
                            />
                        </Field>
                        <div style={{marginTop: '10px', display: 'flex', gap: '10px'}}>
                            <Button
                                appearance="primary"
                                disabled={savingName || !organizationName}
                                onClick={handleSaveOrgName}
                            >
                                {savingName ? <Spinner size="tiny"/> : "Save"}
                            </Button>
                            <Button
                                appearance="secondary"
                                disabled={savingName}
                                onClick={() =>
                                {
                                    setEditMode(false);
                                    setOrganizationName(organization.name || "");
                                }}
                            >
                                Cancel
                            </Button>
                        </div>
                    </div>
                ) : (
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
                )}

                <Divider alignContent="start" appearance="brand">
                    Organization Settings
                </Divider>

                {organizationSettings && <>
                    <Switch
                        checked={organizationSettings.allowShareWithoutPairing}
                        onChange={(_, data) => handleSettingChange('allowShareWithoutPairing', data.checked)}
                        label="Allow sharing without pairing"
                        disabled={savingSettings}
                    />
                    <Switch
                        checked={organizationSettings.allowProfileUpdate}
                        onChange={(_, data) => handleSettingChange('allowProfileUpdate', data.checked)}
                        label="Allow users to update their profiles"
                        disabled={savingSettings}
                    />
                    <Switch
                        checked={organizationSettings.allowEmailUpdate}
                        onChange={(_, data) => handleSettingChange('allowEmailUpdate', data.checked)}
                        label="Allow users to update their email addresses"
                        disabled={savingSettings}
                    />
                </>}

                <Divider alignContent="start" appearance="brand">
                    Contact Details
                </Divider>

                <div style={{display: 'flex', alignItems: 'center', margin: '10px 0'}}>
                    <Text size={500} italic={true} style={{width: '150px'}}>
                        Email
                    </Text>
                    <Text size={500}>
                        <Button
                            appearance="subtle"
                            size="small"
                            icon={<ProfileEditBasicDetailsIcon/>}
                            onClick={onAddOrEditEmail}
                        />
                        {organization?.contactDetails?.email || 'No email added'}
                    </Text>
                </div>

                <div style={{display: 'flex', alignItems: 'center', margin: '10px 0'}}>
                    <Text size={500} italic={true} style={{width: '150px'}}>
                        Phone number
                    </Text>
                    <Text size={500}>
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
                            Add Phone Number
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
            </div>
        )}
    </>
}

export default OrganizationTab;