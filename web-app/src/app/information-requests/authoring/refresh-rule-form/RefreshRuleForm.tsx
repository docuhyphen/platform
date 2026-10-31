import {useState} from "react";
import {Button} from "@fluentui/react-components";
import {DefineInformationRequestRefreshRuleRequest} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import NumberField from "../../shared/number-field/NumberField.tsx";
import {RefreshableDocument} from "../refresh-rules-panel/refreshRuleChoices.ts";
import {useRefreshRuleFormStyles} from "./RefreshRuleFormStyles.tsx";

interface Props {
    documents: RefreshableDocument[];
    busy: boolean;
    onDefine: (definition: DefineInformationRequestRefreshRuleRequest) => void;
}

const DEFAULT_LEAD_DAYS = 30;

const RefreshRuleForm = ({documents, busy, onDefine}: Props) => {
    const styles = useRefreshRuleFormStyles();
    const [requirementKey, setRequirementKey] = useState("");
    const [leadDays, setLeadDays] = useState<number | undefined>(DEFAULT_LEAD_DAYS);
    const chosen = documents.some(document => document.requirementKey === requirementKey);
    const ready = chosen && leadDays !== undefined && Number.isInteger(leadDays) && leadDays >= 0;

    const define = () => {
        if (!ready || leadDays === undefined) return;
        onDefine({requirementKey, leadDays});
    };

    return (
        <div id={"information-request-refresh-rule-form"}
             role={"group"}
             aria-label={"Add a refresh rule"}
             className={styles.form}>
            <ChoiceSelect<string> id={"information-request-refresh-rule-requirement"}
                                  label={"Document to refresh"}
                                  value={chosen ? requirementKey : ""}
                                  placeholder={"Choose a document"}
                                  disabled={busy}
                                  options={documents.map(document => ({
                                      value: document.requirementKey,
                                      label: document.label
                                  }))}
                                  onChange={setRequirementKey}/>
            <NumberField id={"information-request-refresh-rule-lead-days"}
                         label={"Days before expiry"}
                         hint={"A new request is created this many days before the evidence expires."}
                         value={leadDays}
                         min={0}
                         step={1}
                         disabled={busy}
                         onChange={setLeadDays}/>
            <Button id={"information-request-refresh-rule-add"}
                    appearance={"secondary"}
                    shape={"circular"}
                    className={styles.submit}
                    disabled={busy || !ready}
                    onClick={define}>
                Add refresh rule
            </Button>
        </div>
    );
};

export default RefreshRuleForm;
