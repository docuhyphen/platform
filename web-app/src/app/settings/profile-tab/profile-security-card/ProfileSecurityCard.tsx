import {Button, Text} from "@fluentui/react-components";
import {PasswordRegular} from "@fluentui/react-icons";
import ProfileSectionCard from "../profile-section-card/ProfileSectionCard.tsx";
import {useProfileTabStyles} from "../ProfileTabStyles.tsx";

interface ProfileSecurityCardProps
{
    onChangePassword: () => void;
}

const ProfileSecurityCard = (
    {
        onChangePassword
    }: ProfileSecurityCardProps
) =>
{
    const styles = useProfileTabStyles();

    return <ProfileSectionCard
        id={"profile-security-card"}
        title={"Security"}
        description={"Manage access to your account and recovery path."}>
        <div id={"profile-security-content"} className={styles.securityStack}>
            <div id={"profile-password-panel"} className={styles.securityPanel}>
                <div id={"profile-password-copy"} className={styles.contentBlock}>
                    <Text id={"profile-password-title"} size={400} weight={"semibold"}>Password</Text>
                    <Text id={"profile-password-helper"} size={200} className={styles.helperText}>
                        Resetting is available from the sign-in screen using Recover Account.
                    </Text>
                </div>
                <Button
                    id={"button-change-password"}
                    appearance={"secondary"}
                    shape={"circular"}
                    icon={<PasswordRegular/>}
                    onClick={onChangePassword}>
                    Change password
                </Button>
            </div>
        </div>
    </ProfileSectionCard>;
};

export default ProfileSecurityCard;
