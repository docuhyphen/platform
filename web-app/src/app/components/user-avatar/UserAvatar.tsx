import {Avatar, AvatarProps} from "@fluentui/react-components";
import {useAvatarUrl} from "../hooks/useAvatarUrl.ts";

interface UserAvatarProps extends Omit<AvatarProps, "image">
{
    // Avatar marker path from a DTO (or an already-resolved object URL).
    avatarUrl?: string | null;
}

/**
 * Fluent Avatar that renders a user's profile picture when one exists and falls back to the
 * name initials otherwise. The marker path is resolved to an authenticated image source.
 */
const UserAvatar = ({avatarUrl, ...avatarProps}: UserAvatarProps) =>
{
    const resolvedUrl = useAvatarUrl(avatarUrl);

    return (
        <Avatar
            {...avatarProps}
            image={resolvedUrl ? {src: resolvedUrl} : undefined}
        />
    );
};

export default UserAvatar;

