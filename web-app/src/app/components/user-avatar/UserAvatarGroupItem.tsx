import {AvatarGroupItem, AvatarGroupItemProps} from "@fluentui/react-components";
import {useAvatarUrl} from "../hooks/useAvatarUrl.ts";

interface UserAvatarGroupItemProps extends Omit<AvatarGroupItemProps, "image">
{
    // Avatar marker path from a DTO (or an already-resolved object URL).
    avatarUrl?: string | null;
}

/**
 * AvatarGroupItem that renders a group member's profile picture when one exists and falls back
 * to the name initials otherwise. The marker path is resolved to an authenticated image source.
 */
const UserAvatarGroupItem = ({avatarUrl, ...itemProps}: UserAvatarGroupItemProps) =>
{
    const resolvedUrl = useAvatarUrl(avatarUrl);

    return (
        <AvatarGroupItem
            {...itemProps}
            image={resolvedUrl ? {src: resolvedUrl} : undefined}
        />
    );
};

export default UserAvatarGroupItem;

