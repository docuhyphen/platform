import {useCallback, useEffect, useRef, useState} from "react";
import {Badge, Button, Spinner, Text} from "@fluentui/react-components";
import {ArrowClockwiseRegular} from "@fluentui/react-icons";
import {AuditOrganizationIntegrityDto} from "../../models/models.tsx";
import {getOrganizationAuditIntegrity, getPlatformAuditIntegrity} from "../../../services/auditService.ts";
import {AuditScope} from "../auditScope.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {useAuditIntegritySectionStyles} from "./AuditIntegritySectionStyles.tsx";

interface AuditIntegritySectionProps
{
    scope: AuditScope;
}

/** Per-stream integrity (hash chain + archive segment) report section of the Audit workspace. */
const AuditIntegritySection = (
    {
        scope,
    }: AuditIntegritySectionProps
) =>
{
    const styles = useAuditIntegritySectionStyles();
    const [report, setReport] = useState<AuditOrganizationIntegrityDto | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
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
                ? await getOrganizationAuditIntegrity(scope.organizationId)
                : await getPlatformAuditIntegrity();
            if (generation !== requestGenerationRef.current)
            {
                return;
            }
            setReport(result);
        }
        catch (err: unknown)
        {
            if (generation !== requestGenerationRef.current)
            {
                return;
            }
            setError(normalizeApiError(err, "Failed to load integrity report.").message);
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

    return (
        <div id={"audit-integrity-section"} className={styles.container}>
            <div className={styles.summary}>
                <Button
                    id={"button-audit-integrity-refresh"}
                    appearance={"secondary"}
                    shape={"circular"}
                    icon={<ArrowClockwiseRegular/>}
                    onClick={load}
                    disabled={loading}
                >
                    Refresh
                </Button>
                {report && (
                    <Badge
                        id={"audit-integrity-overall-badge"}
                        color={report.allValid ? "success" : "danger"}
                        appearance={"tint"}
                    >
                        {report.allValid ? "All streams valid" : "Integrity issues found"}
                    </Badge>
                )}
            </div>

            {loading && <Spinner size={"small"} label={"Checking integrity..."} labelPosition={"after"}/>}
            {error && <Text id={"audit-integrity-error"} className={styles.errorText}>{error}</Text>}

            {!loading && report && report.streams.length === 0 && (
                <Text id={"audit-integrity-empty"}>No archived streams to verify yet.</Text>
            )}

            {!loading && report?.streams.map((stream) => (
                <div key={stream.streamId} id={`audit-integrity-stream-${stream.streamId}`} className={styles.streamCard}>
                    <div className={styles.streamHeader}>
                        <Text weight={"semibold"}>{stream.streamId}</Text>
                        <Badge color={stream.chainValid ? "success" : "danger"} appearance={"tint"}>
                            Chain {stream.chainValid ? "valid" : "invalid"}
                        </Badge>
                        <Badge color={stream.segmentsValid === stream.segmentsChecked ? "success" : "danger"} appearance={"tint"}>
                            Segments {stream.segmentsValid}/{stream.segmentsChecked}
                        </Badge>
                    </div>
                    <Text size={200}>{stream.chainNote}</Text>
                    {stream.segmentFailureNotes.map((note, index) => (
                        <Text key={index} size={200} className={styles.failureNote}>{note}</Text>
                    ))}
                </div>
            ))}
        </div>
    );
};

export default AuditIntegritySection;
