import {Avatar, Badge, Button, Text} from "@fluentui/react-components";
import {CameraRegular, PersonCircleRegular} from "@fluentui/react-icons";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {useProfileOverviewCardStyles} from "./ProfileOverviewCardStyles.tsx";
import {MailEditIcon, PersonEditIcon, PhoneAddIcon, PhoneEditIcon} from "../../../components/IconBundles.tsx";

interface ProfileOverviewCardProps
{
    appUser: AppUserDetailedDto | null;
    memberSince: string;
    onEditProfile: () => void;
    onEditPhone: () => void;
    onEditEmail: () => void;
    onEditAvatar: () => void;
}

const getDisplayName = (appUser: AppUserDetailedDto | null) =>
{
    const firstName = appUser?.person?.firstName || "";
    const lastName = appUser?.person?.lastName || "";
    return `${firstName} ${lastName}`.trim() || "Your profile";
};

const getAvatarName = (appUser: AppUserDetailedDto | null) =>
{
    return getDisplayName(appUser) === "Your profile" ? "Profile" : getDisplayName(appUser);
};

const ProfileOverviewCard = (
    {
        appUser,
        memberSince,
        onEditProfile,
        onEditPhone,
        onEditEmail,
        onEditAvatar,
    }: ProfileOverviewCardProps
) =>
{
    const styles = useProfileOverviewCardStyles();
    const hasPhoneNumber = !!appUser?.person?.contactDetails?.phoneNumber;
    const phoneNumber = appUser?.person?.contactDetails?.phoneNumber;
    const avatarSrc = appUser?.avatarUrl && appUser.avatarUrl.startsWith("blob:")
        ? appUser.avatarUrl
        : undefined;

    return <section id={"profile-overview-card"} className={styles.card}>
        <div id={"profile-overview-content"} className={styles.content}>
            <div id={"profile-overview-identity"} className={styles.identityBlock}>
                <div id={"profile-overview-avatar-wrapper"} className={styles.avatarWrapper}>
                    <Avatar
                        id={"profile-overview-avatar"}
                        name={getAvatarName(appUser)}
                        size={72}
                        icon={<PersonCircleRegular/>}
                        image={{src: avatarSrc}}
                    />
                    <Button
                        id={"button-edit-profile-picture"}
                        appearance={"primary"}
                        shape={"circular"}
                        size={"small"}
                        icon={<CameraRegular/>}
                        className={styles.avatarEditButton}
                        title={"Edit profile picture"}
                        aria-label={"Edit profile picture"}
                        onClick={onEditAvatar}
                    />
                </div>


                <div id={"profile-overview-copy"} className={styles.copyBlock}>
                    <div id={"profile-overview-title-row"} className={styles.inlineDetailRow}>
                        <Button
                            id={"button-profile-overview-edit"}
                            appearance={"subtle"}
                            shape={"circular"}
                            size={"small"}
                            icon={<PersonEditIcon/>}
                            onClick={onEditProfile}
                        />
                        <Text id={"profile-overview-title"} size={700} weight={"semibold"}>{getDisplayName(appUser)}</Text>
                    </div>
                    <div id={"profile-overview-email-row"} className={styles.inlineDetailRow}>
                        <Button
                            id={"button-edit-email"}
                            appearance={"subtle"}
                            shape={"circular"}
                            size={"small"}
                            icon={<MailEditIcon/>}
                            onClick={onEditEmail}
                        />
                        <Text id={"profile-overview-email"} size={300} className={styles.subtleText}>
                            {appUser?.email || "No email added"}
                        </Text>
                    </div>
                    <div id={"profile-overview-phone-row"}>
                    {hasPhoneNumber ?<>
                            <Button
                                id={"button-edit-phone"}
                                appearance={"subtle"}
                                shape={"circular"}
                                size={"small"}
                                icon={<PhoneEditIcon/>}
                                onClick={onEditPhone}
                            />
                            <Text id={"profile-overview-phone-value"} size={300}>{phoneNumber}</Text>
                    </> :
                        <Button
                            id={"button-add-phone-number"}
                            appearance={"secondary"}
                            shape={"circular"}
                            size={"small"}
                            icon={<PhoneAddIcon/>}
                            onClick={onEditPhone}>
                            Add phone number
                        </Button>
                    }
                    </div>
                    <div id={"profile-overview-badges"} className={styles.badgeRow}>
                        <Badge id={"profile-overview-status-badge"} appearance={"filled"} color={appUser?.isActive ? "success" : "warning"}>
                            {appUser?.isActive ? "Account active" : "Account pending"}
                        </Badge>
                        <Badge id={"profile-overview-meta-member"} appearance={"filled"}>
                            Member since {memberSince}
                        </Badge>
                    </div>
                </div>
            </div>
        </div>
    </section>;
};

export default ProfileOverviewCard;
