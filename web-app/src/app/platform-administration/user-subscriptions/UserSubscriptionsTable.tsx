import {Button, Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "@fluentui/react-components";
import {EditRegular} from "@fluentui/react-icons";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
import {useUserSubscriptionsStyles} from "./UserSubscriptionsStyles.tsx";

interface UserSubscriptionsTableProps
{
    items: PlatformUserSubscriptionPolicy[];
    onEdit: (item: PlatformUserSubscriptionPolicy) => void;
}

const UserSubscriptionsTable = ({items, onEdit}: UserSubscriptionsTableProps) =>
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
                                <Button
                                    id={`platform-user-subscription-edit-${item.appUserId}`}
                                    appearance={"subtle"}
                                    shape={"circular"}
                                    icon={<EditRegular/>}
                                    onClick={() => onEdit(item)}>
                                    Edit
                                </Button>
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

