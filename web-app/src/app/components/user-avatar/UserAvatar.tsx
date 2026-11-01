import {Avatar, AvatarProps} from "@fluentui/react-components";
import {useAvatarUrl} from "../hooks/useAvatarUrl.ts";

interface UserAvatarProps extends Omit<AvatarProps, "image">
{
    avatarUrl?: string | null;
}

const UserAvatar = ({avatarUrl, ...avatarProps}: UserAvatarProps) =>
{
    const resolvedUrl = useAvatarUrl(avatarUrl);

    return (
        <Avatar
            id={"user-avatar-avatar-1"}
            {...avatarProps}
            image={resolvedUrl ? {src: resolvedUrl} : undefined}
        />
    );
};

export default UserAvatar;

