import {useCallback, useEffect, useRef, useState} from "react";
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
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";

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
    const scopeKey = scope.kind === "organization" ? `organization:${scope.organizationId}` : "platform";
    const activeScopeKeyRef = useRef(scopeKey);
    activeScopeKeyRef.current = scopeKey;
    // Bumped on every load() call so a response from a superseded scope switch can detect it is
    // stale and discard itself instead of overwriting state a newer request already populated.
    const requestGenerationRef = useRef(0);

    const load = useCallback(async () =>
    {
        const generation = ++requestGenerationRef.current;
        setLoading(true);
        setError(null);

        try
        {
            const result = scope.kind === "organization"
                ? await listOrganizationAuditExports(scope.organizationId)
                : await listPlatformAuditExports();
            if (generation !== requestGenerationRef.current)
            {
                return;
            }
            setExports(result);
        }
        catch (err: unknown)
        {
            if (generation !== requestGenerationRef.current)
            {
                return;
            }
            setError(normalizeApiError(err, "Failed to load audit exports.").message);
        }
        finally
        {
            if (generation === requestGenerationRef.current)
            {
                setLoading(false);
            }
        }
    }, [scope]);

    useEffect(() =>
    {
        load();
    }, [load]);

    const requestExport = async (request: AuditExportCreateRequestDto) =>
    {
        const mutationScopeKey = scopeKey;
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
            if (activeScopeKeyRef.current !== mutationScopeKey)
            {
                return false;
            }
            await load();
            return true;
        }
        catch (err: unknown)
        {
            if (activeScopeKeyRef.current === mutationScopeKey)
            {
                setSubmitError(normalizeApiError(err, "Failed to request audit export.").message);
            }
            return false;
        }
        finally
        {
            if (activeScopeKeyRef.current === mutationScopeKey)
            {
                setSubmitting(false);
            }
        }
    };

    const approveExport = async (exportId: string) =>
    {
        const mutationScopeKey = scopeKey;
        setError(null);
        try
        {
            if (scope.kind === "organization")
            {
                await approveOrganizationAuditExport(scope.organizationId, exportId);
            }
            else
            {
                await approvePlatformAuditExport(exportId);
            }
            if (activeScopeKeyRef.current !== mutationScopeKey)
            {
                return false;
            }
            if (activeScopeKeyRef.current === mutationScopeKey)
            {
                await load();
            }
        }
        catch (err: unknown)
        {
            if (activeScopeKeyRef.current === mutationScopeKey)
            {
                setError(normalizeApiError(err, "Failed to approve audit export.").message);
            }
        }
    };

    const downloadExport = async (exportItem: AuditExportDto) =>
    {
        const mutationScopeKey = scopeKey;
        setError(null);
        try
        {
            const blob = scope.kind === "organization"
                ? await downloadOrganizationAuditExport(scope.organizationId, exportItem.exportId)
                : await downloadPlatformAuditExport(exportItem.exportId);
            if (activeScopeKeyRef.current === mutationScopeKey)
            {
                saveBlob(blob, `audit-export-${exportItem.exportId}.zip`);
            }
        }
        catch (err: unknown)
        {
            if (activeScopeKeyRef.current === mutationScopeKey)
            {
                setError(normalizeApiError(err, "Failed to download audit export.").message);
            }
        }
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
