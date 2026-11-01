import {Badge, Button, Text} from '@fluentui/react-components';
import {PersonDeleteRegular} from '@fluentui/react-icons';
import {PrincipalGroupDto} from '../../../../services/types/dtos';
import {PrincipalGroupRoleDisplayNames} from '../../../../services/types/roles';
import {RemoveMemberTarget} from '../myGroupsTypes.ts';
import {memberDisplayName, nonOwnerMembers} from '../personalGroupContacts.ts';
import {useManageGroupDialogStyles} from './ManageGroupDialogStyles.tsx';

interface ManageGroupMemberListProps
{
    group: PrincipalGroupDto | null;
    onRemove: (target: RemoveMemberTarget) => void;
}

const ManageGroupMemberList = ({group, onRemove}: ManageGroupMemberListProps) =>
{
    const styles = useManageGroupDialogStyles();
    const nonOwners = group ? nonOwnerMembers(group) : [];

    return (
        <div id={"my-groups-tab-div-4"}>
            <Text id={"my-groups-tab-text-9"}
                  weight="semibold"
                  size={300}>Members</Text>
            <div id={"my-groups-tab-div-5"}
                 className={styles.memberList}>
                {nonOwners.length === 0 ? (
                    <Text id={"my-groups-tab-text-10"}
                          size={200}
                          italic>No members yet.</Text>
                ) : nonOwners.map((m, i) => (
                    <div
                        id={`my-groups-tab-div-6-${i}`}
                        key={i}
                        className={styles.memberRow}
                    >
                        <Text
                            id={"my-groups-tab-text-11"}
                            size={200}
                            className={styles.memberName}
                        >
                            {memberDisplayName(m, 'Unknown')}
                        </Text>
                        <Badge
                            id={"my-groups-tab-badge-2"}
                            size="small"
                            appearance="outline"
                        >
                            {PrincipalGroupRoleDisplayNames[m.groupRole as keyof typeof PrincipalGroupRoleDisplayNames] || m.groupRole}
                        </Badge>
                        <Button
                            id={`remove-member-${i}`}
                            size="small"
                            appearance="subtle"
                            shape={"circular"}
                            icon={<PersonDeleteRegular/>}
                            title="Remove member"
                            onClick={() =>
                            {
                                if (m.user?.id && group)
                                {
                                    onRemove({
                                        groupId: group.id,
                                        userId: m.user.id,
                                        displayName: memberDisplayName(m, 'this member'),
                                    });
                                }
                            }}
                        />
                    </div>
                ))}
            </div>
        </div>
    );
};

export default ManageGroupMemberList;
