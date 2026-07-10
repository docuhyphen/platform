import {useCallback, useEffect, useState} from "react";
import {AuditExportCreateRequestDto, AuditExportDto} from "../../models/models.tsx";
import {
    approveOrganizationAuditExport,
    approvePlatformAuditExport,
    downloadOrganizationAuditExport,
    downloadPlatformAuditExport,
    listOrganizationAuditExports,
    listPlatformAuditExports,
    requestOrganizationAuditExport,
    requestPlatformAuditExport,
} from "../../../services/auditService.ts";
import {AuditScope} from "../auditScope.ts";

const saveBlob = (blob: Blob, filename: string) =>
{
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    window.URL.revokeObjectURL(url);
};

/**
 * Loading/mutation state machine for the export list section of the Audit workspace, kept
 * separate from `AuditExportsSection` so that component stays focused on rendering.
 */
export const useAuditExports = (scope: AuditScope) =>
{
    const [exports, setExports] = useState<AuditExportDto[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState<boolean>(false);
    const [submitError, setSubmitError] = useState<string | null>(null);

    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);

        try
        {
            const result = scope.kind === "organization"
                ? await listOrganizationAuditExports(scope.organizationId)
                : await listPlatformAuditExports();
            setExports(result);
        }
        catch (err: unknown)
        {
            const message = err instanceof Error ? err.message : "Failed to load audit exports.";
            setError(message);
        }
        finally
        {
            setLoading(false);
        }
    }, [scope]);

    useEffect(() =>
    {
        load();
    }, [load]);

    const requestExport = async (request: AuditExportCreateRequestDto) =>
    {
        setSubmitting(true);
        setSubmitError(null);

        try
        {
            if (scope.kind === "organization")
            {
                await requestOrganizationAuditExport(scope.organizationId, request);
            }
            else
            {
                await requestPlatformAuditExport(request);
            }
            await load();
            return true;
        }
        catch (err: unknown)
        {
            const message = err instanceof Error ? err.message : "Failed to request audit export.";
            setSubmitError(message);
            return false;
        }
        finally
        {
            setSubmitting(false);
        }
    };

    const approveExport = async (exportId: string) =>
    {
        if (scope.kind === "organization")
        {
            await approveOrganizationAuditExport(scope.organizationId, exportId);
        }
        else
        {
            await approvePlatformAuditExport(exportId);
        }
        await load();
    };

    const downloadExport = async (exportItem: AuditExportDto) =>
    {
        const blob = scope.kind === "organization"
            ? await downloadOrganizationAuditExport(scope.organizationId, exportItem.exportId)
            : await downloadPlatformAuditExport(exportItem.exportId);
        saveBlob(blob, `audit-export-${exportItem.exportId}.zip`);
    };

    return {
        exports,
        loading,
        error,
        submitting,
        submitError,
        requestExport,
        approveExport,
        downloadExport,
    };
};
