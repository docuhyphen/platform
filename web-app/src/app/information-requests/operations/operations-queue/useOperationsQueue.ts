import {useCallback, useState} from "react";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {sendInformationRequestReminders} from "../../../../services/informationRequestAdministrationService.ts";
import {getInformationRequestOperations} from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestOperationsRowDto} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";
import {useStillMounted} from "../../shared/useStillMounted.ts";
import {OPERATIONS_CSV_FILE_NAME, operationsCsv, saveCsvFile} from "../operationsCsv.ts";
import {reminderOutcomeSentence} from "./reminderOutcome.ts";
import {useLearnedAssignees} from "./useLearnedAssignees.ts";
import {useOperationsQueueFilter} from "./useOperationsQueueFilter.ts";
import {useReminderSelection} from "./useReminderSelection.ts";

const EXPORT_PAGE_SIZE = 200;

export const useOperationsQueue = () =>
{
    const filters = useOperationsQueueFilter();
    const {filter} = filters;
    const scope = JSON.stringify({...filter, offset: 0});
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const keyed = useStableIdempotencyKey();
    const stillMounted = useStillMounted();

    const load = useCallback(() => getInformationRequestOperations(filter), [filter]);
    const page = useLoadedValue(load, "The operations queue could not be loaded.", scope);
    const assignees = useLearnedAssignees(page);
    const selection = useReminderSelection(scope, page.value?.items ?? []);

    const run = async (work: () => Promise<void>) =>
    {
        setBusy(true);
        setError(null);
        setNotice(null);
        try
        {
            await work();
        }
        catch (caught: unknown)
        {
            setError(informationRequestRefusalMessage(caught, "The change could not be made."));
        }
        finally
        {
            setBusy(false);
        }
    };

    const sendReminders = () => run(async () =>
    {
        const requestIds = [...selection.selected].sort();
        const results = await keyed("remind", requestIds, key => sendInformationRequestReminders(requestIds, key));
        setNotice(reminderOutcomeSentence(results));
        selection.clear();
        page.reload();
    });

    const exportCsv = () => run(async () =>
    {
        const rows: InformationRequestOperationsRowDto[] = [];
        let total = Number.POSITIVE_INFINITY;
        while (rows.length < total)
        {
            const next = await getInformationRequestOperations({
                ...filter,
                limit: EXPORT_PAGE_SIZE,
                offset: rows.length
            });
            if (!stillMounted()) return;
            total = next.total;
            if (next.items.length === 0) break;
            rows.push(...next.items);
        }
        saveCsvFile(OPERATIONS_CSV_FILE_NAME, operationsCsv(rows));
    });

    return {
        ...filters,
        page,
        assignees,
        selected: selection.selected,
        selectionFull: selection.full,
        toggle: selection.toggle,
        busy,
        error,
        notice,
        sendReminders,
        exportCsv,
    };
};

export type OperationsQueueState = ReturnType<typeof useOperationsQueue>;
