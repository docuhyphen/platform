import {Button, Text} from "@fluentui/react-components";
import {PasswordRegular, ShieldKeyholeRegular} from "@fluentui/react-icons";
import {MfaMethod} from "../../../models/models.tsx";
import ProfileSectionCard from "../profile-section-card/ProfileSectionCard.tsx";
import {useProfileTabStyles} from "../ProfileTabStyles.tsx";

interface ProfileSecurityCardProps
{
    onChangePassword: () => void;
    mfaMethod: MfaMethod;
    emailFallbackEnabled: boolean;
    onConfigureMfa: () => void;
}

const ProfileSecurityCard = (
    {
        onChangePassword,
        mfaMethod,
        emailFallbackEnabled,
        onConfigureMfa,
    }: ProfileSecurityCardProps
) =>
{
    const styles = useProfileTabStyles();

    return <ProfileSectionCard
        id={"profile-security-card"}
        title={"Security"}
        description={"Manage access to your account and recovery path."}>
        <div
            id={"profile-security-content"}
            className={styles.securityStack}>
            <div
                id={"profile-password-panel"}
                className={styles.securityPanel}>
                <div
                    id={"profile-password-copy"}
                    className={styles.contentBlock}>
                    <Text
                        id={"profile-password-title"}
                        size={400}
                        weight={"semibold"}>
                        Password
                    </Text>
                    <Text
                        id={"profile-password-helper"}
                        size={200}
                        className={styles.helperText}>
                        Resetting is available from the sign-in screen using Recover Account.
                    </Text>
                </div>
                <Button
                    id={"button-change-password"}
                    appearance={"secondary"}
                    size={"small"}
                    shape={"circular"}
                    icon={<PasswordRegular/>}
                    onClick={onChangePassword}>
                    Change password
                </Button>
            </div>
            <div
                id={"profile-mfa-panel"}
                className={styles.securityPanel}>
                <div
                    id={"profile-mfa-copy"}
                    className={styles.contentBlock}>
                    <Text
                        id={"profile-mfa-title"}
                        size={400}
                        weight={"semibold"}>
                        Multi-factor authentication
                    </Text>
                    <Text
                        id={"profile-mfa-helper"}
                        size={200}
                        className={styles.helperText}>
                        {mfaMethod === 'EMAIL'
                            ? 'Email verification is your current method.'
                            : `${mfaMethod === 'GOOGLE_AUTHENTICATOR' ? 'Google' : 'Microsoft'} Authenticator is enabled${emailFallbackEnabled ? ' with email fallback.' : '.'}`}
                    </Text>
                </div>
                <Button
                    id={"button-configure-mfa"}
                    appearance={"secondary"}
                    shape={"circular"}
                    size={"small"}
                    icon={<ShieldKeyholeRegular/>}
                    onClick={onConfigureMfa}>
                    Configure MFA
                </Button>
            </div>
        </div>
    </ProfileSectionCard>;
};

export default ProfileSecurityCard;
