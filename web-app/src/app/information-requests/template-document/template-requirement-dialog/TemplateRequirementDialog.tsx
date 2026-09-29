import {useEffect, useState} from "react";
import {Tab, TabList, Text} from "@fluentui/react-components";
import {InformationRequestRequirementType} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {problemsAt, useTemplateDocument} from "../TemplateDocumentContext.ts";
import {newRequirement} from "../templateDraftDocument.ts";
import RequirementQuestionFields from "../requirement-question-fields/RequirementQuestionFields.tsx";
import RequirementAnsweringFields from "../requirement-answering-fields/RequirementAnsweringFields.tsx";
import RequirementEvidenceFields from "../requirement-evidence-fields/RequirementEvidenceFields.tsx";
import RequirementEvidenceAttributes from "../requirement-evidence-attributes/RequirementEvidenceAttributes.tsx";
import RequirementConfirmationFields from "../requirement-confirmation-fields/RequirementConfirmationFields.tsx";
import RequirementLinkFields from "../requirement-link-fields/RequirementLinkFields.tsx";
import {RequirementDraft, withRequirementSaved} from "./requirementDraft.ts";
import {useTemplateRequirementDialogStyles} from "./TemplateRequirementDialogStyles.tsx";

type RequirementTab = "question" | "answering" | "evidence" | "confirmation" | "links";

interface TemplateRequirementDialogProps
{
    sectionIndex: number;
    requirementIndex?: number;
    onClose: () => void;
}

const PROMPT_INPUT_ID = "template-requirement-prompt-input";

const TemplateRequirementDialog = ({sectionIndex, requirementIndex, onClose}: TemplateRequirementDialogProps) =>
{
    const styles = useTemplateRequirementDialogStyles();
    const {document, readOnly, problems, update} = useTemplateDocument();
    const existing = requirementIndex === undefined ? undefined : document.sections[sectionIndex].requirements[requirementIndex];
    const [draft, setDraft] = useState<RequirementDraft>(() =>
        existing ?? newRequirement(InformationRequestRequirementType.FIELD, "", ""));
    const [tab, setTab] = useState<RequirementTab>("question");
    const rowProblems = requirementIndex === undefined ? [] : problemsAt(problems, sectionIndex, requirementIndex);
    const change = (next: Partial<RequirementDraft>) => setDraft(current => ({...current, ...next}));
    const fieldProps = {id: "template-requirement", draft, readOnly, onChange: change};

    useEffect(() =>
    {
        const timer = window.setTimeout(() => window.document.getElementById(PROMPT_INPUT_ID)?.focus(), 0);
        return () => window.clearTimeout(timer);
    }, []);

    const save = () =>
    {
        update(current => withRequirementSaved(current, sectionIndex, requirementIndex, {
            ...draft,
            requirementKey: draft.requirementKey.trim(),
            prompt: draft.prompt.trim(),
            helpText: draft.helpText?.trim() || undefined,
        }));
        onClose();
    };

    return (
        <EditorDialog id={"template-requirement-dialog"}
                      title={readOnly ? "Requirement" : existing ? "Edit requirement" : "Add requirement"}
                      readOnly={readOnly}
                      wide={true}
                      confirmDisabled={!draft.prompt.trim() || !draft.requirementKey.trim()}
                      onConfirm={save}
                      onDismiss={onClose}>
            {rowProblems.map(problem => (
                <Text key={problem.key}
                      id={`template-requirement-${problem.key}`}
                      className={styles.problem}>
                    {problem.message}
                </Text>
            ))}
            <TabList id={"template-requirement-tabs"}
                     selectedValue={tab}
                     onTabSelect={(_, data) => setTab(data.value as RequirementTab)}
                     className={styles.tabs}>
                <Tab id={"template-requirement-tab-question"}
                     value={"question"}>
                    Question
                </Tab>
                <Tab id={"template-requirement-tab-answering"}
                     value={"answering"}>
                    Answering
                </Tab>
                {draft.requirementType === InformationRequestRequirementType.DOCUMENT && (
                    <Tab id={"template-requirement-tab-evidence"}
                         value={"evidence"}>
                        Evidence
                    </Tab>
                )}
                {draft.requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION && (
                    <Tab id={"template-requirement-tab-confirmation"}
                         value={"confirmation"}>
                        Confirmation
                    </Tab>
                )}
                <Tab id={"template-requirement-tab-links"}
                     value={"links"}>
                    Links
                </Tab>
            </TabList>
            {tab === "question" && (
                <RequirementQuestionFields {...fieldProps}
                                           keyLocked={existing !== undefined}
                                           onDraftReplaced={setDraft}/>
            )}
            {tab === "answering" && <RequirementAnsweringFields {...fieldProps} onDraftReplaced={setDraft}/>}
            {tab === "evidence" && (
                <>
                    <RequirementEvidenceFields {...fieldProps}/>
                    <RequirementEvidenceAttributes {...fieldProps}/>
                </>
            )}
            {tab === "confirmation" && <RequirementConfirmationFields {...fieldProps}/>}
            {tab === "links" && <RequirementLinkFields {...fieldProps}/>}
        </EditorDialog>
    );
};

export default TemplateRequirementDialog;
