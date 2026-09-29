import {useEffect, useState} from "react";
import {getInformationRequestResponseWorkspace} from "../../../services/informationRequestRuntimeService.ts";
import {InformationRequestReviewItemDto} from "../../models/models.tsx";
import {requirementPromptsOf} from "../shared/requirementPrompts.ts";
import {humanizedKey} from "../submission/submissionLabels.ts";

export const useReviewRequirementLabels = (requestId: string) =>
{
    const [prompts, setPrompts] = useState<Record<string, string>>({});

    useEffect(() =>
    {
        let active = true;
        getInformationRequestResponseWorkspace(requestId)
            .then(workspace =>
            {
                if (active) setPrompts(requirementPromptsOf(workspace));
            })
            .catch(() => undefined);
        return () =>
        {
            active = false;
        };
    }, [requestId]);

    return (item: Pick<InformationRequestReviewItemDto, "requirementId" | "requirementKey">): string =>
        prompts[item.requirementId] ?? humanizedKey(item.requirementKey);
};
