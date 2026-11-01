import {AvatarGroup, AvatarGroupPopover, partitionAvatarGroupItems, Text, Tooltip} from '@fluentui/react-components';
import {PrincipalGroupMemberDto} from '../../../../services/types/dtos';
import UserAvatarGroupItem from '../../../components/user-avatar/UserAvatarGroupItem.tsx';
import {memberDisplayName} from '../personalGroupContacts.ts';

interface GroupMemberAvatarsProps
{
    members: PrincipalGroupMemberDto[];
}

const GroupMemberAvatars = ({members}: GroupMemberAvatarsProps) =>
{
    if (members.length === 0)
    {
        return <Text id={"my-groups-tab-text-7"}
                     size={200}>-</Text>;
    }

    const items = members.map(m => ({
        name: memberDisplayName(m, 'Unknown'),
        key: m.user?.id || m.user?.email || String(Math.random()),
        avatarUrl: m.user?.avatarUrl,
    }));
    const {inlineItems, overflowItems} = partitionAvatarGroupItems({items, maxInlineItems: 5});

    return (
        <AvatarGroup id={"my-groups-tab-avatar-group-1"}
                     size={24}
                     layout="stack">
            {inlineItems?.map(item => (
                <Tooltip key={item.key}
                         content={item.name}
                         relationship="label">
                    <UserAvatarGroupItem name={item.name}
                                         avatarUrl={item.avatarUrl}/>
                </Tooltip>
            ))}
            {overflowItems?.length > 0 && (
                <AvatarGroupPopover>
                    {overflowItems.map(item => (
                        <UserAvatarGroupItem id={`my-groups-tab-user-avatar-group-item-2-${item.key}`}
                                             key={item.key}
                                             name={item.name}
                                             avatarUrl={item.avatarUrl}/>
                    ))}
                </AvatarGroupPopover>
            )}
        </AvatarGroup>
    );
};

export default GroupMemberAvatars;
