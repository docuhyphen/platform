import {useEffect, useState} from "react";
import {useTemplateDocument} from "./TemplateDocumentContext.ts";
import {TemplateDraftProblem, TemplateDraftTarget} from "./templateDraftRules.ts";

type ItemPanel = "groups" | "conditions" | "review";

const indexOf = (target: TemplateDraftTarget): number | undefined =>
{
    if (target.panel === "groups") return target.groupIndex;
    if (target.panel === "conditions") return target.ruleIndex;
    if (target.panel === "review") return target.stageIndex;
    return undefined;
};

export const problemsForItem = (problems: TemplateDraftProblem[], panel: ItemPanel, index: number): string[] =>
    problems.filter(problem => problem.target.panel === panel && indexOf(problem.target) === index)
        .map(problem => problem.message);

export const useFocusedItem = (panel: ItemPanel) =>
{
    const {focus, clearFocus} = useTemplateDocument();
    const [editing, setEditing] = useState<number | "new" | null>(null);
    const [highlighted, setHighlighted] = useState<number | undefined>(undefined);

    useEffect(() =>
    {
        if (focus?.panel !== panel) return;
        const index = indexOf(focus);
        setHighlighted(index);
        if (index !== undefined) setEditing(index);
        clearFocus();
    }, [clearFocus, focus, panel]);

    return {editing, setEditing, highlighted};
};
