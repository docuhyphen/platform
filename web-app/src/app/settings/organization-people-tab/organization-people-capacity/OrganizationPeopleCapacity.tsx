import {Caption1, ProgressBar} from "@fluentui/react-components";
import {OrgMemberCapacityResponse} from "../../../models/models.tsx";
import {useOrganizationPeopleCapacityStyles} from "./OrganizationPeopleCapacityStyles.tsx";

interface OrganizationPeopleCapacityProps
{
    capacity: OrgMemberCapacityResponse | null;
}

const OrganizationPeopleCapacity = ({capacity}: OrganizationPeopleCapacityProps) =>
{
    const styles = useOrganizationPeopleCapacityStyles();
    if (!capacity) return null;

    return (
        <div
            id="organization-people-capacity"
            className={styles.container}
        >
            <div id={"organization-people-capacity-div-1"}
                 className={styles.row}>
                <Caption1 id={"organization-people-capacity-caption1-1"}><strong id={"organization-people-capacity-strong-1"}>Member capacity</strong> | Tier: {capacity.tierCode}</Caption1>
                <Caption1 id={"organization-people-capacity-caption1-2"}>
                    {capacity.activeUsers}{capacity.maxUsers != null ? ` / ${capacity.maxUsers}` : " / Unlimited"}
                </Caption1>
            </div>
            {capacity.maxUsers != null && (
                <ProgressBar
                    id={"organization-people-capacity-progress-bar-1"}
                    value={capacity.activeUsers / capacity.maxUsers}
                    color={capacity.atCap ? "error" : capacity.nearCap ? "warning" : "brand"}
                    thickness="medium"
                />
            )}
            {capacity.atCap && (
                <Caption1 id={"organization-people-capacity-caption1-3"}
                          className={styles.atCap}>
                    Organization has reached its user limit. Upgrade your plan to add more members.
                </Caption1>
            )}
            {!capacity.atCap && capacity.nearCap && (
                <Caption1 id={"organization-people-capacity-caption1-4"}
                          className={styles.nearCap}>Approaching user limit.</Caption1>
            )}
        </div>
    );
};

export default OrganizationPeopleCapacity;
