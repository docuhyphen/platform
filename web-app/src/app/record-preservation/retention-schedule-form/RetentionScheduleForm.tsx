import {useState} from "react";
import {Button, Field, Input, MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {LoadedValue} from "../../../hooks/useLoadedValue.ts";
import {
    INFORMATION_REQUEST_RECORD_TYPE,
    publishRecordRetentionSchedule
} from "../../../services/recordPreservationService.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {isStaleInformationRequestCommand} from "../../information-requests/shared/informationRequestRefusal.ts";
import {useStableIdempotencyKey} from "../../information-requests/shared/useStableIdempotencyKey.ts";
import {RecordRetentionScheduleDto} from "../../models/models.tsx";
import {retentionFormState} from "../retention-schedule/retentionSchedule.ts";
import {useRetentionScheduleFormStyles} from "./RetentionScheduleFormStyles.tsx";

interface RetentionScheduleFormProps {
    schedule: LoadedValue<RecordRetentionScheduleDto>;
}

const STALE_SCHEDULE =
    "The retention schedule changed after it was read. The latest version is shown; publish again if it still needs to change.";

const RetentionScheduleForm = ({schedule}: RetentionScheduleFormProps) => {
    const styles = useRetentionScheduleFormStyles();
    const [minimum, setMinimum] = useState("");
    const [disposal, setDisposal] = useState("");
    const [busy, setBusy] = useState(false);
    const keyed = useStableIdempotencyKey();
    const [error, setError] = useState<string | null>(null);
    const form = retentionFormState(minimum, disposal);
    const scheduleETag = schedule.value?.scheduleETag;

    const publish = async () => {
        if (form.minimumDays === undefined || scheduleETag === undefined) return;
        setBusy(true);
        setError(null);
        try {
            const request = {minimumRetentionDays: form.minimumDays, disposalAfterDays: form.disposalDays};
            await keyed(`retention-schedule:${scheduleETag}`, request, key =>
                publishRecordRetentionSchedule(INFORMATION_REQUEST_RECORD_TYPE, request, scheduleETag, key));
            setMinimum("");
            setDisposal("");
            schedule.reload();
        } catch (caught: unknown) {
            if (isStaleInformationRequestCommand(caught)) {
                setError(STALE_SCHEDULE);
                schedule.reload();
                return;
            }
            setError(normalizeApiError(caught, "The retention schedule could not be published.").message);
        } finally {
            setBusy(false);
        }
    };

    return (
        <div id={"record-retention-schedule-form"}
             className={styles.form}>
            {error && (
                <MessageBar id={"record-retention-schedule-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"record-retention-schedule-error-body"}>{error}</MessageBarBody>
                </MessageBar>
            )}
            <div id={"record-retention-schedule-fields"}
                 className={styles.fields}>
                <Field id={"record-retention-minimum-field"}
                       label={"Keep at least (days)"}
                       required
                       validationMessage={form.minimumProblem}>
                    <Input id={"record-retention-minimum"}
                           inputMode={"numeric"}
                           value={minimum}
                           disabled={busy}
                           onChange={(_, data) => setMinimum(data.value)}/>
                </Field>
                <Field id={"record-retention-disposal-field"}
                       label={"Dispose after (days, optional)"}
                       validationMessage={form.disposalProblem}>
                    <Input id={"record-retention-disposal"}
                           inputMode={"numeric"}
                           value={disposal}
                           disabled={busy}
                           onChange={(_, data) => setDisposal(data.value)}/>
                </Field>
            </div>
            {schedule.error && (
                <Text id={"record-retention-schedule-unreadable"}
                      className={styles.note}>
                    The current schedule could not be read, so no new version can be published until it is.
                </Text>
            )}
            <div id={"record-retention-schedule-actions"}
                 className={styles.actions}>
                <Button id={"record-retention-publish-btn"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={busy || !form.valid || scheduleETag === undefined}
                        onClick={publish}>
                    Publish new version
                </Button>
            </div>
        </div>
    );
};

export default RetentionScheduleForm;
