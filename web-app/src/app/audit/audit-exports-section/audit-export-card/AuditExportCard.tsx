import {Badge, Button, Text} from "@fluentui/react-components";
import {ArrowDownloadRegular, CheckmarkRegular} from "@fluentui/react-icons";
import {AuditExportDto} from "../../../models/models.tsx";
import {useAuditExportCardStyles} from "./AuditExportCardStyles.tsx";

interface AuditExportCardProps
{
    exportItem: AuditExportDto;
    canApprove: boolean;
    canDownload: boolean;
    onApprove: (exportId: string) => void;
    onDownload: (exportItem: AuditExportDto) => void;
}

/** One evidence export row: status, approval progress, and approve/download actions. */
const AuditExportCard = (
    {
        exportItem,
        canApprove,
        canDownload,
        onApprove,
        onDownload,
    }: AuditExportCardProps
) =>
{
    const styles = useAuditExportCardStyles();

    return (
        <div id={`audit-export-card-${exportItem.exportId}`} className={styles.card}>
            <div className={styles.header}>
                <Text weight={"semibold"}>{exportItem.purpose}</Text>
                <Badge appearance={"tint"} color={exportItem.status === "READY" ? "success" : "informative"}>
                    {exportItem.status}
                </Badge>
                <Text size={200}>
                    {exportItem.approvalCount}/{exportItem.requiredApprovals} approvals
                </Text>
            </div>
            <div className={styles.actions}>
                {canApprove && exportItem.status === "APPROVAL_PENDING" && (
                    <Button
                        id={`button-audit-export-approve-${exportItem.exportId}`}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<CheckmarkRegular/>}
                        onClick={() => onApprove(exportItem.exportId)}
                    >
                        Approve
                    </Button>
                )}
                {canDownload && exportItem.status === "READY" && (
                    <Button
                        id={`button-audit-export-download-${exportItem.exportId}`}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<ArrowDownloadRegular/>}
                        onClick={() => onDownload(exportItem)}
                    >
                        Download
                    </Button>
                )}
            </div>
        </div>
    );
};

export default AuditExportCard;
