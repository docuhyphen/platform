import {useEffect, useState} from "react";
import {CommunicationSummaryDto, WorkflowStepSpecDraft} from "../../../models/models.tsx";
import {getCommunication} from "../../../../services/communicationService.ts";

interface UseStepCommunicationArgs
{
    step: WorkflowStepSpecDraft;
    platformMode: boolean;
    patch: (p: Partial<WorkflowStepSpecDraft>) => void;
}

export function useStepCommunication({step, platformMode, patch}: UseStepCommunicationArgs)
{
    const [pickerOpen, setPickerOpen] = useState(false);
    const [selectedName, setSelectedName] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!platformMode && step.communicationId && step.type === "NOTIFICATION")
        {
            getCommunication(step.communicationId)
                .then(t => setSelectedName(t.name))
                .catch(() => setSelectedName(step.communicationId ?? null));
        }
        else
        {
            setSelectedName(null);
        }
    }, [platformMode, step.communicationId, step.type]);

    const openPicker = () => setPickerOpen(true);
    const closePicker = () => setPickerOpen(false);

    const clearCommunication = () =>
    {
        patch({communicationId: undefined});
        setSelectedName(null);
    };

    const selectCommunication = (communication: CommunicationSummaryDto) =>
    {
        patch({communicationId: communication.id});
        setSelectedName(communication.name);
        setPickerOpen(false);
    };

    return {pickerOpen, selectedName, openPicker, closePicker, clearCommunication, selectCommunication};
}
