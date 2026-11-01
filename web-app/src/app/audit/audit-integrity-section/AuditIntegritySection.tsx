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
        <div id={"audit-integrity-section"}
             className={styles.container}>
            <div id={"audit-integrity-section-div-1"}
                 className={styles.summary}>
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

            {loading && <Spinner id={"audit-integrity-section-spinner-1"}
                                 size={"small"}
                                 label={"Checking integrity..."}
                                 labelPosition={"after"}/>}
            {error && <Text id={"audit-integrity-error"}
                            className={styles.errorText}>{error}</Text>}

            {!loading && report && report.streams.length === 0 && (
                <Text id={"audit-integrity-empty"}>No archived streams to verify yet.</Text>
            )}

            {!loading && report?.streams.map((stream) => (
                <div key={stream.streamId}
                     id={`audit-integrity-stream-${stream.streamId}`}
                     className={styles.streamCard}>
                    <div id={"audit-integrity-section-div-2"}
                         className={styles.streamHeader}>
                        <Text id={"audit-integrity-section-text-1"}
                              weight={"semibold"}>{stream.streamId}</Text>
                        <Badge id={"audit-integrity-section-badge-1"}
                               color={stream.chainValid ? "success" : "danger"}
                               appearance={"tint"}>
                            Chain {stream.chainValid ? "valid" : "invalid"}
                        </Badge>
                        <Badge id={"audit-integrity-section-badge-2"}
                               color={stream.segmentsValid === stream.segmentsChecked ? "success" : "danger"}
                               appearance={"tint"}>
                            Segments {stream.segmentsValid}/{stream.segmentsChecked}
                        </Badge>
                    </div>
                    <Text id={"audit-integrity-section-text-2"}
                          size={200}>{stream.chainNote}</Text>
                    {stream.segmentFailureNotes.map((note, index) => (
                        <Text id={`audit-integrity-section-text-3-${index}`}
                              key={index}
                              size={200}
                              className={styles.failureNote}>{note}</Text>
                    ))}
                </div>
            ))}
        </div>
    );
};

export default AuditIntegritySection;
