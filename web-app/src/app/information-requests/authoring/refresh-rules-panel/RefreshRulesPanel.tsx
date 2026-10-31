import {Button, Text} from "@fluentui/react-components";
import {
    DefineInformationRequestRefreshRuleRequest,
    InformationRequestRefreshRuleDto,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import RefreshRuleForm from "../refresh-rule-form/RefreshRuleForm.tsx";
import {leadDaysSentence, refreshableDocuments, refreshStateSentence} from "./refreshRuleChoices.ts";
import {useRefreshRulesPanelStyles} from "./RefreshRulesPanelStyles.tsx";

interface Props {
    rules: InformationRequestRefreshRuleDto[];
    sections: InformationRequestTemplateSectionDto[];
    canDefine: boolean;
    canRefresh: boolean;
    busy: boolean;
    onDefine: (definition: DefineInformationRequestRefreshRuleRequest) => void;
    onRefresh: (refreshRuleId: string) => void;
}

const RefreshRulesPanel = ({rules, sections, canDefine, canRefresh, busy, onDefine, onRefresh}: Props) => {
    const styles = useRefreshRulesPanelStyles();
    const documents = refreshableDocuments(sections);
    const labelOf = (requirementKey: string): string =>
        documents.find(document => document.requirementKey === requirementKey)?.label ?? requirementKey;
    const available = documents.filter(document => !rules.some(rule => rule.requirementKey === document.requirementKey));
    if (rules.length === 0 && (!canDefine || available.length === 0)) return null;

    return (
        <div id={"information-request-refresh-rules"}
             role={"group"}
             aria-labelledby={"information-request-refresh-rules-title"}
             className={styles.panel}>
            <Text id={"information-request-refresh-rules-title"}
                  weight={"semibold"}>
                Expiry refreshes
            </Text>
            {rules.length > 0 && (
                <ul id={"information-request-refresh-rule-list"}
                    className={styles.list}>
                    {rules.map(rule => (
                        <li key={rule.id}
                            id={`information-request-refresh-rule-${rule.id}`}
                            className={styles.row}>
                            <div id={`information-request-refresh-rule-${rule.id}-text`}
                                 className={styles.text}>
                                <Text id={`information-request-refresh-rule-${rule.id}-document`}>
                                    {labelOf(rule.requirementKey)}
                                </Text>
                                <Text id={`information-request-refresh-rule-${rule.id}-lead`}
                                      size={200}
                                      className={styles.muted}>
                                    {leadDaysSentence(rule.leadDays)}
                                </Text>
                                <Text id={`information-request-refresh-rule-${rule.id}-state`}
                                      size={200}
                                      className={styles.muted}>
                                    {refreshStateSentence(rule)}
                                </Text>
                            </div>
                            {canRefresh && rule.refreshDue === true && (
                                <Button id={`information-request-refresh-rule-${rule.id}-refresh`}
                                        appearance={"secondary"}
                                        shape={"circular"}
                                        disabled={busy}
                                        aria-label={`Refresh ${labelOf(rule.requirementKey)} now`}
                                        onClick={() => onRefresh(rule.id)}>
                                    Refresh now
                                </Button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {canDefine && available.length > 0 && (
                <RefreshRuleForm documents={available}
                                 busy={busy}
                                 onDefine={onDefine}/>
            )}
        </div>
    );
};

export default RefreshRulesPanel;
