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
} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {updateOrganizationSettings} from "../../../services/organizationApi";
import {ContactDetailsDetailedDto, OrganizationDetailedDto, OrganizationSettingsDto} from "../../models/models.tsx";
import {
    ProfileEditBasicDetailsIcon,
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
import TrustedOrganizationsTab from "../trusted-organizations-tab/TrustedOrganizationsTab.tsx";
import TemplatesTab from "../blueprints-tab/BlueprintsTab.tsx";
import OrganizationDetailsTab from "../organization-details-tab/OrganizationDetailsTab.tsx";
import OrganizationEmptyStateIllustration
    from "./organization-empty-state-illustration/OrganizationEmptyStateIllustration.tsx";


const OrganizationTab = () =>
{
    const tabIds = {
        organization: "OrganizationTab",
        people: "PeopleTab",
        groups: "GroupsTab",
        organizationTrusted: "TrustedOrganizationsTab",
        templates: "TemplatesTab",
        myGroups: "MyGroupsTab",
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
    const [selectedValue, setSelectedValue] = useState<TabValue>(tabIds.organization);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };
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
        catch (err: unknown)
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
    }, []);

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
            <div className={styles.errorWrapper}>
                {error}
            </div>
        )}

        {organization && !fetchingOrganization && !organization.isActive && (
            <section className={styles.orgOnboardingContainer}>
                <MessageBar intent={"warning"}>
                    <MessageBarBody>
                        <Text weight="semibold">Organization Pending Verification</Text>
                        <br/>
                    </MessageBarBody>
                </MessageBar>
                <div className={styles.onboardingRow}>
                    <Text>
                        Your organization <Text weight="semibold">{organization.name}</Text> has been registered
                        and is currently under review. We are verifying your details and you will receive a
                        confirmation email once the process is complete. You can check back here at any time for
                        status updates.
                    </Text>
                </div>
                <div className={styles.onboardingRow}>
                    <Text size={300}>
                        Registration Number: {organization.registrationNumber}
                    </Text>
                </div>
                <div className={styles.onboardingRow}>
                    <Text size={300}>
                        Contact Email: {organization.contactDetails?.email || 'Not provided'}
                    </Text>
                </div>
                <div>
                    <Text size={300}>
                        Contact Phone: {organization.contactDetails?.phoneNumber || 'Not provided'}
                    </Text>
                </div>
                <div className={styles.onboardingRow}>
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
                    <Tab id="TrustedOrganizationsTab"
                         value={tabIds.organizationTrusted}>
                        Trusted Organizations
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
                    {selectedValue === tabIds.organizationTrusted && <TrustedOrganizationsTab/>}
                    {selectedValue === tabIds.templates && <TemplatesTab/>}
                    {selectedValue === tabIds.auth && organization?.id && (
                        <AuthSessionPolicySection organizationId={organization.id}/>
                    )}
                </div>
            </div>
        )}

        {!organization && !fetchingOrganization && <>
            <section
                id={"organization-empty-state"}
                className={styles.orgOnboardingContainer}>
                <OrganizationEmptyStateIllustration/>
                <Text
                    id={"organization-empty-state-message"}
                    align={"center"}>
                    You are not part of an organization. You can onboard your organization to use the full potential of the platform.
                </Text>
                <div id={"organization-empty-state-action"}>
                    <Button
                        id={"button-org-register"}
                        shape={"circular"}
                        appearance={"primary"}
                        onClick={() => setOnboardingDialogOpen(true)}
                        icon={<></>}
                    >
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
