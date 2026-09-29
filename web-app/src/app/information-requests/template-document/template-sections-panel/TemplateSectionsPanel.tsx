import {useEffect, useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {AddIcon} from "../../../components/IconBundles.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import TemplateSectionCard from "../template-section-card/TemplateSectionCard.tsx";
import TemplateSectionDialog from "../template-section-dialog/TemplateSectionDialog.tsx";
import TemplateRequirementDialog from "../template-requirement-dialog/TemplateRequirementDialog.tsx";
import {useTemplateSectionsPanelStyles} from "./TemplateSectionsPanelStyles.tsx";

interface RequirementEditing
{
    sectionIndex: number;
    requirementIndex?: number;
}

const TemplateSectionsPanel = () =>
{
    const styles = useTemplateSectionsPanelStyles();
    const {document, readOnly, focus, clearFocus} = useTemplateDocument();
    const [sectionEditing, setSectionEditing] = useState<number | "new" | null>(null);
    const [requirementEditing, setRequirementEditing] = useState<RequirementEditing | null>(null);
    const [highlighted, setHighlighted] = useState<RequirementEditing | null>(null);

    useEffect(() =>
    {
        if (focus?.panel !== "sections" || focus.sectionIndex === undefined) return;
        const target = {sectionIndex: focus.sectionIndex, requirementIndex: focus.requirementIndex};
        setHighlighted(target);
        if (target.requirementIndex !== undefined) setRequirementEditing(target);
        else setSectionEditing(target.sectionIndex);
        clearFocus();
    }, [clearFocus, focus]);

    return (
        <div id={"template-sections-panel"}
             className={styles.panel}>
            {document.sections.length === 0 && (
                <Text id={"template-sections-empty"}
                      className={styles.muted}>
                    This Template has no sections yet. Each section groups the requirements a party answers together.
                </Text>
            )}
            {document.sections.map((section, sectionIndex) => (
                <TemplateSectionCard key={`${section.sectionKey}-${sectionIndex}`}
                                     sectionIndex={sectionIndex}
                                     highlightedRequirement={highlighted?.sectionIndex === sectionIndex
                                         ? highlighted.requirementIndex
                                         : undefined}
                                     onEditSection={() => setSectionEditing(sectionIndex)}
                                     onEditRequirement={requirementIndex =>
                                         setRequirementEditing({sectionIndex, requirementIndex})}
                                     onAddRequirement={() => setRequirementEditing({sectionIndex})}/>
            ))}
            {!readOnly && (
                <Button id={"template-add-section"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        className={styles.addButton}
                        onClick={() => setSectionEditing("new")}>
                    Add section
                </Button>
            )}
            {sectionEditing !== null && (
                <TemplateSectionDialog sectionIndex={sectionEditing === "new" ? undefined : sectionEditing}
                                       onClose={() => setSectionEditing(null)}/>
            )}
            {requirementEditing && (
                <TemplateRequirementDialog sectionIndex={requirementEditing.sectionIndex}
                                           requirementIndex={requirementEditing.requirementIndex}
                                           onClose={() => setRequirementEditing(null)}/>
            )}
        </div>
    );
};

export default TemplateSectionsPanel;
