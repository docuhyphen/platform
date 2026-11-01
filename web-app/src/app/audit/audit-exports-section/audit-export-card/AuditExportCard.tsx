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
        <div id={`audit-export-card-${exportItem.exportId}`}
             className={styles.card}>
            <div id={"audit-export-card-div-1"}
                 className={styles.header}>
                <Text id={"audit-export-card-text-1"}
                      weight={"semibold"}>{exportItem.purpose}</Text>
                <Badge id={"audit-export-card-badge-1"}
                       appearance={"tint"}
                       color={exportItem.status === "READY" ? "success" : "informative"}>
                    {exportItem.status}
                </Badge>
                <Text id={"audit-export-card-text-2"}
                      size={200}>
                    {exportItem.approvalCount}/{exportItem.requiredApprovals} approvals
                </Text>
            </div>
            <div id={"audit-export-card-div-2"}
                 className={styles.actions}>
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
