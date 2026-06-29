import {Switch, Text} from "@fluentui/react-components";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {useProfileTabStyles} from "../ProfileTabStyles.tsx";
import ProfileSectionCard from "../profile-section-card/ProfileSectionCard.tsx";

interface ProfileNotificationsCardProps
{
    appUser: AppUserDetailedDto | null;
    onNotifyLoginChange: (_, data) => Promise<void>;
}

const ProfileNotificationsCard = (
    {
        appUser,
        onNotifyLoginChange
    }: ProfileNotificationsCardProps
) =>
{
    const styles = useProfileTabStyles();

    return <ProfileSectionCard
        id={"profile-notifications-card"}
        title={"Sign-in notifications"}
        description={"Choose whether DocuHyphen emails you after each sign-in."}>
        <div id={"profile-notifications-content"} className={styles.notificationCardContent}>
            {appUser && (
                <Switch
                    id={"switch-notify-login"}
                    checked={appUser.settings.notifyLogin}
                    onChange={onNotifyLoginChange}
                    label={"Email me every time I sign in"}
                />
            )}
            <Text id={"profile-notifications-helper"} size={200} className={styles.helperText}>
                Use this if you want a simple alert whenever your account is accessed.
            </Text>
        </div>
    </ProfileSectionCard>;
};

export default ProfileNotificationsCard;
