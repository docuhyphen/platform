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
import {DeleteRegular} from "@fluentui/react-icons";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {AppAdminDto} from "../../../../services/types/dtos.ts";
import {useAppAdministratorsTableStyles} from "./AppAdministratorsTableStyles.tsx";

interface AppAdministratorsTableProps
{
    admins: AppAdminDto[];
    busy: boolean;
    onRevoke: (assignmentId: string) => void;
}

const AppAdministratorsTable = ({
    admins,
    busy,
    onRevoke,
}: AppAdministratorsTableProps) =>
{
    const {appUser} = useAuth();
    const styles = useAppAdministratorsTableStyles();

    return (
        <div
            id={"app-administrators-table-scroll-container"}
            className={styles.scrollContainer}>
            <Table
                id={"app-administrators-table"}
                size={"small"}
                className={styles.table}>
                <TableHeader id={"app-administrators-table-header"}>
                    <TableRow id={"app-administrators-table-header-row"}>
                        <TableHeaderCell id={"app-administrators-name-header"}>Name</TableHeaderCell>
                        <TableHeaderCell id={"app-administrators-email-header"}>Email</TableHeaderCell>
                        <TableHeaderCell id={"app-administrators-granted-header"}>Granted At</TableHeaderCell>
                        <TableHeaderCell
                            id={"app-administrators-actions-header"}
                            className={styles.actionsCell}>
                            Actions
                        </TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody id={"app-administrators-table-body"}>
                    {admins.map((admin) => (
                        <TableRow
                            id={`app-administrator-row-${admin.assignmentId}`}
                            key={admin.assignmentId}>
                            <TableCell>{`${admin.firstName ?? ""} ${admin.lastName ?? ""}`.trim() || "-"}</TableCell>
                            <TableCell>{admin.email}</TableCell>
                            <TableCell>
                                {admin.grantedAt ? new Date(admin.grantedAt).toLocaleString() : "-"}
                            </TableCell>
                            <TableCell className={styles.actionsCell}>
                                {admin.appUserId === appUser?.id ? (
                                    <Badge id={`app-administrator-current-${admin.assignmentId}`}>You</Badge>
                                ) : (
                                    <Button
                                        id={`btn-revoke-admin-${admin.assignmentId}`}
                                        size={"small"}
                                        appearance={"subtle"}
                                        shape={"circular"}
                                        icon={<DeleteRegular/>}
                                        disabled={busy}
                                        onClick={() => onRevoke(admin.assignmentId)}>
                                        Revoke
                                    </Button>
                                )}
                            </TableCell>
                        </TableRow>
                    ))}
                    {admins.length === 0 && (
                        <TableRow id={"app-administrators-empty-row"}>
                            <TableCell colSpan={4}>
                                <Text id={"app-administrators-empty-message"}>
                                    No App Administrators found.
                                </Text>
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </div>
    );
};

export default AppAdministratorsTable;
