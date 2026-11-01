import {AvatarGroupItem, AvatarGroupItemProps} from "@fluentui/react-components";
import {useAvatarUrl} from "../hooks/useAvatarUrl.ts";

interface UserAvatarGroupItemProps extends Omit<AvatarGroupItemProps, "image">
{
    avatarUrl?: string | null;
}

const UserAvatarGroupItem = ({avatarUrl, ...itemProps}: UserAvatarGroupItemProps) =>
{
    const resolvedUrl = useAvatarUrl(avatarUrl);

    return (
        <AvatarGroupItem
            id={"user-avatar-group-item-avatar-group-item-1"}
            {...itemProps}
            image={resolvedUrl ? {src: resolvedUrl} : undefined}
        />
    );
};

export default UserAvatarGroupItem;

