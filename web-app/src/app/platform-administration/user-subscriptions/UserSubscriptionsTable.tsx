import {Button, Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "@fluentui/react-components";
import {EditRegular} from "@fluentui/react-icons";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
import {useUserSubscriptionsStyles} from "./UserSubscriptionsStyles.tsx";

interface UserSubscriptionsTableProps
{
    items: PlatformUserSubscriptionPolicy[];
    onEdit: (item: PlatformUserSubscriptionPolicy) => void;
    onTrial: (item: PlatformUserSubscriptionPolicy) => void;
    onEndTrial: (item: PlatformUserSubscriptionPolicy) => void;
    onConvertTrial: (item: PlatformUserSubscriptionPolicy) => void;
}

const hasActiveTrial = (item: PlatformUserSubscriptionPolicy): boolean =>
    item.subscriptionStatus === "TRIALING"
    && item.currentPeriodEnd !== null
    && new Date(item.currentPeriodEnd).getTime() > Date.now();

const canStartTrial = (item: PlatformUserSubscriptionPolicy): boolean =>
    item.billingFrequency === null
    && (item.subscriptionStatus === "ACTIVE" || item.subscriptionStatus === "TRIALING")
    && !(item.planCode === "PERSONAL" && item.subscriptionStatus === "ACTIVE");

const UserSubscriptionsTable = ({
    items,
    onEdit,
    onTrial,
    onEndTrial,
    onConvertTrial,
}: UserSubscriptionsTableProps) =>
{
    const styles = useUserSubscriptionsStyles();
    return (
        <div
            id={"platform-user-subscriptions-table-scroll"}
            className={styles.tableScroll}>
            <Table
                id={"platform-user-subscriptions-table"}
                className={styles.table}
                size={"small"}>
                <TableHeader id={"platform-user-subscriptions-table-header"}>
                    <TableRow id={"platform-user-subscriptions-header-row"}>
                        <TableHeaderCell id={"platform-user-subscriptions-user-header"}>User</TableHeaderCell>
                        <TableHeaderCell id={"platform-user-subscriptions-plan-header"}>Plan</TableHeaderCell>
                        <TableHeaderCell id={"platform-user-subscriptions-status-header"}>Status</TableHeaderCell>
                        <TableHeaderCell id={"platform-user-subscriptions-period-header"}>Period end</TableHeaderCell>
                        <TableHeaderCell
                            id={"platform-user-subscriptions-actions-header"}
                            className={styles.actions}>Actions</TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody id={"platform-user-subscriptions-table-body"}>
                    {items.map((item) => (
                        <TableRow
                            id={`platform-user-subscription-row-${item.appUserId}`}
                            key={item.appUserId}>
                            <TableCell>{item.displayName ? `${item.displayName} (${item.email})` : item.email}</TableCell>
                            <TableCell>{item.planCode}</TableCell>
                            <TableCell>{item.subscriptionStatus}</TableCell>
                            <TableCell>{item.currentPeriodEnd ? new Date(item.currentPeriodEnd).toLocaleDateString() : "Not set"}</TableCell>
                            <TableCell className={styles.actions}>
                                <div
                                    id={`platform-user-subscription-actions-${item.appUserId}`}
                                    className={styles.actionButtons}>
                                    <Button
                                        id={`platform-user-subscription-trial-${item.appUserId}`}
                                        appearance={"subtle"}
                                        shape={"circular"}
                                        disabled={!hasActiveTrial(item) && !canStartTrial(item)}
                                        onClick={() => onTrial(item)}>
                                        {hasActiveTrial(item) ? "Extend trial" : "Start trial"}
                                    </Button>
                                    {item.subscriptionStatus === "TRIALING" && (
                                        <>
                                            <Button
                                                id={`platform-user-subscription-end-trial-${item.appUserId}`}
                                                appearance={"subtle"}
                                                shape={"circular"}
                                                onClick={() => onEndTrial(item)}>
                                                End trial
                                            </Button>
                                            <Button
                                                id={`platform-user-subscription-convert-trial-${item.appUserId}`}
                                                appearance={"subtle"}
                                                shape={"circular"}
                                                onClick={() => onConvertTrial(item)}>
                                                Convert to paid
                                            </Button>
                                        </>
                                    )}
                                    <Button
                                        id={`platform-user-subscription-edit-${item.appUserId}`}
                                        appearance={"subtle"}
                                        shape={"circular"}
                                        icon={<EditRegular/>}
                                        onClick={() => onEdit(item)}>
                                        Edit
                                    </Button>
                                </div>
                            </TableCell>
                        </TableRow>
                    ))}
                    {items.length === 0 && (
                        <TableRow id={"platform-user-subscriptions-empty-row"}>
                            <TableCell colSpan={5}>No registered user subscriptions match this search.</TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </div>
    );
};

export default UserSubscriptionsTable;
