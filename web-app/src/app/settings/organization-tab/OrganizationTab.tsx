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
    PairOrgTabIcon,
    ProfileEditBasicDetailsIcon, SettingsAppAdminsIcon,
    SettingsAppSettingsTabIcon, SettingsDeviceSessionsTabIcon, SettingsLinkedAccountsTabIcon, SettingsMyGroupsTabIcon,
    SettingsOrganizationGroupsTabIcon,
    SettingsOrganizationPeopleTabIcon,
    SettingsOrganizationTabIcon,
    SettingsProfileTabIcon
} from "../../components/IconBundles.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import EmailManagementDialog, {EmailManagementMode} from "../../components/email-management/EmailManagementDialog.tsx";
import {useOrganizationTabStyles} from "./OrganizationTabStyles.tsx";
import OrganizationDetailsEditDialog from "./details-edit-dialog/OrganizationDetailsEditDialog.tsx";
import {AxiosError} from "axios";
import OrganizationOnboardingDialog from "./organization-onboarding-dialog/OrganizationOnboardingDialog.tsx";
import {AuthSessionPolicySection} from "./AuthSessionPolicySection.tsx";
import ProfileTab from "../profile-tab/ProfileTab.tsx";
import LinkedAccountsTab from "../linked-accounts-tab/LinkedAccountsTab.tsx";
import SessionsTab from "../sessions-tab/SessionsTab.tsx";
import AppSettingsTab from "../app-settings-tab/AppSettingsTab.tsx";
import MyGroupsTab from "../my-groups-tab/MyGroupsTab.tsx";
import OrganizationPeopleTab from "../organization-people-tab/OrganizationPeopleTab.tsx";
import OrganizationGroupsTab from "../organization-groups-tab/OrganizationGroupsTab.tsx";
import OrganizationPairingTab from "../organization-pairing-tab/OrganizationPairingTab.tsx";
import AppAdminsTab from "../app-admins-tab/AppAdminsTab.tsx";
import TemplatesTab from "../blueprints-tab/BlueprintsTab.tsx";
import OrganizationDetailsTab from "../organization-details-tab/OrganizationDetailsTab.tsx";


const OrganizationTab = () =>
{
    const tabIds = {
        organization: "OrganizationTab",
        people: "PeopleTab",
        groups: "GroupsTab",
        organizationPairing: "OrganizationPairingTab",
        templates: "TemplatesTab",
        myGroups: "MyGroupsTab",
        appAdmins: "AppAdminsTab",
        auth: "AuthTab",
    }

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
    const [selectedValue, setSelectedValue] = useState<TabValue>(tabIds.organization);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };
    const canManageOrganization =  () =>
    {
        return appUserPersonOrganization?.isActive && (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN')
    }

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

                <div className={styles.tabListWrapper}>
                <TabList selectedValue={selectedValue}
                         onTabSelect={onTabSelect}
                         size="medium">
                    <Tab id="OrganizationTab"
                         value={tabIds.organization}>
                        Details
                    </Tab>
                    <Tab id="PeopleTab"
                         value={tabIds.people}>
                        People
                    </Tab>
                    <Tab id="GroupsTab"
                         value={tabIds.groups}>
                        Groups/Teams
                    </Tab>
                    <Tab id="OrganiationPairingTab"
                         value={tabIds.organizationPairing}>
                        Org Pairing
                    </Tab>
                    <Tab id="AppAdminsTab"
                         value={tabIds.appAdmins}>
                        Administrators
                    </Tab>
                    <Tab id="AuthTab"
                         value={tabIds.auth}>
                        Authentication
                    </Tab>
                </TabList>
                </div>

                <div className={styles.tabsContainer}
                     id={"settings-tabs"}>
                    {selectedValue === tabIds.organization && <OrganizationDetailsTab/>}
                    {selectedValue === tabIds.people && <OrganizationPeopleTab/>}
                    {selectedValue === tabIds.groups &&
                        <OrganizationGroupsTab appUserPersonOrganization={appUserPersonOrganization}/>}
                    {selectedValue === tabIds.organizationPairing && <OrganizationPairingTab/>}
                    {selectedValue === tabIds.appAdmins && <AppAdminsTab/>}
                    {selectedValue === tabIds.templates && <TemplatesTab/>}
                    {selectedValue === tabIds.auth && organization?.id && (
                        <AuthSessionPolicySection organizationId={organization.id}/>
                    )}
                </div>
            </div>
        )}

        {!organization && !fetchingOrganization && <>
            <section className={styles.orgOnboardingContainer}>
                <Text>
                    You are not part of an organization. You can onboard your organization to use the full potential of the platform.
                </Text>
                <div>
                    <Button shape={"circular"}
                            appearance={"secondary"}
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