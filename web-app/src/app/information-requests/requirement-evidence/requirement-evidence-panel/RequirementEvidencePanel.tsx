import RequirementEvidenceContent from "../requirement-evidence-content/RequirementEvidenceContent.tsx";
import {useRequirementEvidenceSettings} from "../RequirementEvidenceContext.ts";
import {RequirementEvidencePanelProps} from "../RequirementEvidenceTypes.ts";

const RequirementEvidencePanel = (props: RequirementEvidencePanelProps) =>
{
    const settings = useRequirementEvidenceSettings();
    return settings
        ? <RequirementEvidenceContent {...props}
                                      settings={settings}/>
        : null;
};

export default RequirementEvidencePanel;
