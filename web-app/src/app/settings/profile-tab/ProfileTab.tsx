import {useState} from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue} from "@fluentui/react-components";
import {useProfileTabStyles} from "./ProfileTabStyles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {formatDate} from "../../helpers.ts";
import {useProfileTabDialogs} from "./useProfileTabDialogs.ts";
import {useLoginNotificationSetting} from "./useLoginNotificationSetting.ts";
import ProfileOverviewCard from "./profile-overview-card/ProfileOverviewCard.tsx";
import ProfileSecurityCard from "./profile-security-card/ProfileSecurityCard.tsx";
import ProfileNotificationsCard from "./profile-notifications-card/ProfileNotificationsCard.tsx";
import ProfileSecurityEvents from "./profile-security-events/ProfileSecurityEvents.tsx";
import ProfileTabDialogs from "./profile-tab-dialogs/ProfileTabDialogs.tsx";

type ProfileSubTab = "details" | "security";

const ProfileTab = () =>
{
    const {appUser} = useAuth();
    const styles = useProfileTabStyles();
    const [subTab, setSubTab] = useState<TabValue>("details" satisfies ProfileSubTab);
    const dialogs = useProfileTabDialogs();
    const {notifyLoginChange} = useLoginNotificationSetting();

    const memberSince = appUser?.createdDate ? formatDate(appUser.createdDate) : "Not available";

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSubTab(data.value);
    };

    return <>
        <div id={"profile-tab-container"}
             className={styles.tabsContainer}>
            <div id={"profile-tab-div-1"}
                 className={styles.tabListWrapper}>
                <TabList
                    id={"profile-tab-subtab-list"}
                    selectedValue={subTab}
                    onTabSelect={onTabSelect}
                    size="medium"
                >
                    <Tab id={"profile-tab-details-subtab"}
                         value={"details" satisfies ProfileSubTab}>
                        Details
                    </Tab>
                    <Tab id={"profile-tab-security-subtab"}
                         value={"security" satisfies ProfileSubTab}>
                        Security Activity
                    </Tab>
                </TabList>
            </div>

            <div id={"profile-tab-div-2"}
                 className={styles.tabContent}>
                {subTab === "details" && (
                    <div id={"profile-tab-details-content"}
                         className={styles.detailsScrollableContent}>
                        <ProfileOverviewCard
                            appUser={appUser}
                            memberSince={memberSince}
                            onEditProfile={() => dialogs.setIsBasicDetailsDialogOpen(true)}
                            onEditPhone={() => dialogs.openPhoneManagement(!!appUser?.person.contactDetails?.phoneNumber)}
                            onEditEmail={() => dialogs.setIsEmailUpdateDialogOpen(true)}
                            onEditAvatar={() => dialogs.setIsProfilePictureDialogOpen(true)}
                        />

                        <div id={"profile-tab-card-grid"}
                             className={styles.cardGrid}>
                            <ProfileSecurityCard
                                onChangePassword={() => dialogs.setIsPasswordResetDialogOpen(true)}
                                mfaMethod={appUser?.mfaMethod || 'EMAIL'}
                                emailFallbackEnabled={appUser?.emailMfaFallbackEnabled || false}
                                onConfigureMfa={() => dialogs.setIsMfaDialogOpen(true)}
                            />

                            <ProfileNotificationsCard
                                appUser={appUser}
                                onNotifyLoginChange={notifyLoginChange}
                            />
                        </div>
                    </div>
                )}

                {subTab === "security" && (
                    <div id={"profile-tab-security-content"}
                         className={styles.securityTabContent}>
                        <ProfileSecurityEvents/>
                    </div>
                )}
            </div>
        </div>

        <ProfileTabDialogs dialogs={dialogs}/>
    </>;
};

export default ProfileTab;
