import {useState} from "react";
import {Button, Spinner, Text} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AuditExportCreateRequestDto, Capability} from "../../models/models.tsx";
import {AddIcon} from "../../components/IconBundles.tsx";
import {AuditScope} from "../auditScope.ts";
import AuditExportRequestDialog from "./audit-export-request-dialog/AuditExportRequestDialog.tsx";
import AuditExportCard from "./audit-export-card/AuditExportCard.tsx";
import {useAuditExportsSectionStyles} from "./AuditExportsSectionStyles.tsx";
import {useAuditExports} from "./useAuditExports.ts";

interface AuditExportsSectionProps
{
    scope: AuditScope;
}

/** Export request/approval/download list section of the Audit workspace. */
const AuditExportsSection = (
    {
        scope,
    }: AuditExportsSectionProps
) =>
{
    const styles = useAuditExportsSectionStyles();
    const {hasCapability, currentSession} = useAuth();
    const [dialogOpen, setDialogOpen] = useState<boolean>(false);
    const canApprove = hasCapability(Capability.AUDIT_EXPORT_APPROVE);
    const canExport = hasCapability(
        scope.kind === "organization" ? Capability.ORG_AUDIT_EXPORT : Capability.APP_AUDIT_EXPORT
    );
    const canDownloadAsCustodian = hasCapability(
        scope.kind === "organization" ? Capability.ORG_POLICY_MANAGE : Capability.APP_ADMIN
    );

    const {
        exports,
        loading,
        error,
        submitting,
        submitError,
        requestExport,
        approveExport,
        downloadExport,
    } = useAuditExports(scope);

    const onRequestExport = async (request: AuditExportCreateRequestDto) =>
    {
        const succeeded = await requestExport(request);
        if (succeeded)
        {
            setDialogOpen(false);
        }
    };

    return (
        <div id={"audit-exports-section"} className={styles.container}>
            <div
                id={"audit-exports-header"}
                className={styles.header}
            >
                <Text
                    id={"audit-exports-title"}
                    weight={"semibold"}
                >Evidence exports</Text>
                {canExport && (
                    <Button
                        id={"button-audit-export-request-open"}
                        appearance={"primary"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        onClick={() => setDialogOpen(true)}
                    >
                        Request export
                    </Button>
                )}
            </div>

            {loading && <Spinner size={"small"} label={"Loading exports..."} labelPosition={"after"}/>}
            {error && <Text id={"audit-exports-error"} className={styles.errorText}>{error}</Text>}
            {!loading && exports.length === 0 && (
                <Text id={"audit-exports-empty"}>No audit exports requested yet.</Text>
            )}

            {!loading && exports.map((exportItem) => (
                <AuditExportCard
                    key={exportItem.exportId}
                    exportItem={exportItem}
                    canApprove={canApprove && exportItem.requestedByUserId !== currentSession?.userId}
                    canDownload={canExport && (
                        exportItem.requestedByUserId === currentSession?.userId || canDownloadAsCustodian
                    )}
                    onApprove={approveExport}
                    onDownload={downloadExport}
                />
            ))}

            <AuditExportRequestDialog
                open={dialogOpen}
                submitting={submitting}
                error={submitError}
                onDismiss={() => setDialogOpen(false)}
                onSubmit={onRequestExport}
            />
        </div>
    );
};

export default AuditExportsSection;
