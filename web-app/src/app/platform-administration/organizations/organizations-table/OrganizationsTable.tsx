import {
    Badge,
    Button,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {EditRegular} from "@fluentui/react-icons";
import {PlatformOrganizationSummary} from "../../../../services/types/platformOrganizations.ts";
import {useOrganizationsTableStyles} from "./OrganizationsTableStyles.tsx";

interface OrganizationsTableProps
{
    organizations: PlatformOrganizationSummary[];
    onEdit: (organization: PlatformOrganizationSummary) => void;
}

const entitlementSummary = (organization: PlatformOrganizationSummary): string =>
    organization.featureEntitlements.length === 0
        ? "None"
        : organization.featureEntitlements
            .map((item) => `${item.featureCode}: ${item.enabled ? "On" : "Off"}`)
            .join(", ");

const OrganizationsTable = ({organizations, onEdit}: OrganizationsTableProps) =>
{
    const styles = useOrganizationsTableStyles();

    return (
        <div
            id={"platform-organizations-table-scroll-container"}
            className={styles.scrollContainer}>
            <Table
                id={"platform-organizations-table"}
                size={"small"}
                className={styles.table}>
                <TableHeader
                    id={"platform-organizations-table-header"}
                    className={styles.tableHeader}>
                        <TableRow id={"platform-organizations-header-row"}>
                            <TableHeaderCell id={"platform-organizations-name-header"}>Organization</TableHeaderCell>
                            <TableHeaderCell id={"platform-organizations-registration-header"}>
                                Registration number
                            </TableHeaderCell>
                            <TableHeaderCell id={"platform-organizations-status-header"}>Status</TableHeaderCell>
                        <TableHeaderCell id={"platform-organizations-tier-header"}>Tier</TableHeaderCell>
                        <TableHeaderCell id={"platform-organizations-capacity-header"}>Usage</TableHeaderCell>
                        <TableHeaderCell id={"platform-organizations-features-header"}>Features</TableHeaderCell>
                        <TableHeaderCell
                            id={"platform-organizations-actions-header"}
                            className={styles.actions}>Actions</TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody id={"platform-organizations-table-body"}>
                    {organizations.map((organization) => (
                        <TableRow
                            id={`platform-organization-row-${organization.organizationId}`}
                            key={organization.organizationId}>
                            <TableCell>
                                <Text weight={"semibold"}>{organization.name}</Text>
                            </TableCell>
                            <TableCell>{organization.registrationNumber}</TableCell>
                            <TableCell>
                                <Badge
                                    id={`platform-organization-active-status-${organization.organizationId}`}
                                    appearance={"tint"}>
                                    {organization.active ? "Active" : "Inactive"}
                                </Badge>
                                {" "}
                                <Badge
                                    id={`platform-organization-verification-status-${organization.organizationId}`}
                                    appearance={"outline"}>
                                    {organization.verificationComplete ? "Verified" : "Unverified"}
                                </Badge>
                            </TableCell>
                            <TableCell>{organization.tierCode}</TableCell>
                            <TableCell>
                                {organization.activeUsers} / {organization.maxUsers ?? "Unlimited"}
                            </TableCell>
                            <TableCell className={styles.entitlements}>
                                {entitlementSummary(organization)}
                            </TableCell>
                            <TableCell className={styles.actions}>
                                <Button
                                    id={`platform-organization-edit-${organization.organizationId}`}
                                    appearance={"subtle"}
                                    shape={"circular"}
                                    icon={<EditRegular/>}
                                    onClick={() => onEdit(organization)}>
                                    Edit account
                                </Button>
                            </TableCell>
                        </TableRow>
                    ))}
                    {organizations.length === 0 && (
                        <TableRow id={"platform-organizations-empty-row"}>
                            <TableCell colSpan={7}>
                                <Text id={"platform-organizations-empty-message"}>
                                    No organizations match the current filters.
                                </Text>
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </div>
    );
};

export default OrganizationsTable;
