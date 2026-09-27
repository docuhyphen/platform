import {useCallback, useState} from "react";
import {Button, Field, Input, MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../hooks/useLoadedValue.ts";
import {
    getRecordRetentionSchedule,
    INFORMATION_REQUEST_RECORD_TYPE,
    publishRecordRetentionSchedule,
} from "../../../services/recordPreservationService.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import LoadedPanel from "../../information-requests/operations/loaded-panel/LoadedPanel.tsx";
import {RecordRetentionScheduleVersionDto} from "../../models/models.tsx";
import {useRetentionScheduleCardStyles} from "./RetentionScheduleCardStyles.tsx";

interface RetentionScheduleCardProps
{
    canManage: boolean;
}

const wholeDays = (raw: string): number | undefined => /^\d+$/.test(raw.trim()) ? Number(raw.trim()) : undefined;

const describe = (version: RecordRetentionScheduleVersionDto): string =>
    version.disposalAfterDays === undefined
        ? `Version ${version.versionNumber}: kept at least ${version.minimumRetentionDays} days after finishing, never disposed automatically.`
        : `Version ${version.versionNumber}: kept at least ${version.minimumRetentionDays} days, disposed ${version.disposalAfterDays} days after finishing.`;

const RetentionScheduleCard = ({canManage}: RetentionScheduleCardProps) =>
{
    const styles = useRetentionScheduleCardStyles();
    const load = useCallback(() => getRecordRetentionSchedule(INFORMATION_REQUEST_RECORD_TYPE), []);
    const schedule = useLoadedValue(load, "The retention schedule could not be loaded.");
    const [minimum, setMinimum] = useState("");
    const [disposal, setDisposal] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const minimumDays = wholeDays(minimum);
    const disposalDays = disposal.trim() ? wholeDays(disposal) : undefined;
    const valid = minimumDays !== undefined &&
        (!disposal.trim() || (disposalDays !== undefined && disposalDays >= minimumDays));

    const publish = async () =>
    {
        if (minimumDays === undefined) return;
        setBusy(true);
        setError(null);
        try
        {
            await publishRecordRetentionSchedule(INFORMATION_REQUEST_RECORD_TYPE, {minimumRetentionDays: minimumDays, disposalAfterDays: disposalDays});
            setMinimum("");
            setDisposal("");
            schedule.reload();
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(caught, "The retention schedule could not be published.").message);
        }
        finally
        {
            setBusy(false);
        }
    };

    return (
        <div id={"record-retention-schedule"}
             className={styles.card}>
            <Text id={"record-retention-schedule-title"}
                  weight={"semibold"}>
                Information Request retention
            </Text>
            <LoadedPanel idPrefix={"record-retention-schedule"}
                         loaded={schedule}
                         loadingLabel={"Loading the retention schedule"}>
                {value => (
                    <Text id={"record-retention-schedule-current"}>
                        {value.current ? describe(value.current) : "No retention schedule is published. Finished requests are kept until one is."}
                    </Text>
                )}
            </LoadedPanel>
            {canManage && (
                <div id={"record-retention-schedule-form"}
                     className={styles.form}>
                    {error && (
                        <MessageBar id={"record-retention-schedule-error"}
                                    intent={"error"}>
                            <MessageBarBody>{error}</MessageBarBody>
                        </MessageBar>
                    )}
                    <div id={"record-retention-schedule-fields"}
                         className={styles.fields}>
                        <Field id={"record-retention-minimum-field"}
                               label={"Keep at least (days)"}
                               required
                               validationMessage={minimum.trim() && minimumDays === undefined ? "Enter whole days" : undefined}>
                            <Input id={"record-retention-minimum"}
                                   inputMode={"numeric"}
                                   value={minimum}
                                   disabled={busy}
                                   onChange={(_, data) => setMinimum(data.value)}/>
                        </Field>
                        <Field id={"record-retention-disposal-field"}
                               label={"Dispose after (days, optional)"}
                               validationMessage={disposal.trim() && !valid ? "Enter whole days no shorter than the minimum" : undefined}>
                            <Input id={"record-retention-disposal"}
                                   inputMode={"numeric"}
                                   value={disposal}
                                   disabled={busy}
                                   onChange={(_, data) => setDisposal(data.value)}/>
                        </Field>
                    </div>
                    <div id={"record-retention-schedule-actions"}
                         className={styles.actions}>
                        <Button id={"record-retention-publish-btn"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !valid}
                                onClick={publish}>
                            Publish new version
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default RetentionScheduleCard;
