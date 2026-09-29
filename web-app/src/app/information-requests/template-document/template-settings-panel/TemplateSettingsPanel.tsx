import {Text} from "@fluentui/react-components";
import {InformationRequestSubmissionMode, InformationRequestSubmissionStageOrdering} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {ChoiceOption, optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {requestSchemasOf, useTemplateDocument} from "../TemplateDocumentContext.ts";
import {submissionModeLabels, submissionStageOrderingLabels} from "../templateAuthoringLabels.ts";
import {useTemplateSettingsPanelStyles} from "./TemplateSettingsPanelStyles.tsx";

const KEPT_VERSION = "kept-version";
const NO_SCHEMA = "";

const TemplateSettingsPanel = () =>
{
    const styles = useTemplateSettingsPanelStyles();
    const {document, readOnly, schemas, problems, update} = useTemplateDocument();
    const requestSchemas = requestSchemasOf(schemas);
    const chosen = requestSchemas.find(schema => schema.latestPublishedVersion?.id === document.schemaVersionId);
    const schemaValue = chosen?.id ?? (document.schemaVersionId ? KEPT_VERSION : NO_SCHEMA);
    const schemaOptions: ChoiceOption<string>[] = [
        {value: NO_SCHEMA, label: "No typed answers"},
        ...(schemaValue === KEPT_VERSION ? [{value: KEPT_VERSION, label: "The Schema Version this Template already uses"}] : []),
        ...requestSchemas.map(schema => ({
            value: schema.id,
            label: `${schema.displayName} (version ${schema.latestPublishedVersion?.versionNumber ?? 1})`,
        })),
    ];
    const settingsProblems = problems.filter(problem => problem.target.panel === "settings");

    return (
        <div id={"template-settings-panel"}
             className={styles.panel}>
            {settingsProblems.map(problem => (
                <Text key={problem.key}
                      id={`template-settings-${problem.key}`}
                      className={styles.problem}>
                    {problem.message}
                </Text>
            ))}
            <ChoiceSelect id={"template-settings-schema-select"}
                          label={"Request Schema"}
                          value={schemaValue}
                          options={schemaOptions}
                          disabled={readOnly}
                          hint={"Typed answers are recorded against the Fields of this published Schema Version."}
                          onChange={value =>
                          {
                              if (value === KEPT_VERSION) return;
                              const schemaVersionId = requestSchemas.find(schema => schema.id === value)?.latestPublishedVersion?.id;
                              update(current => ({...current, schemaVersionId}));
                          }}/>
            <ChoiceSelect id={"template-settings-submission-select"}
                          label={"Submission"}
                          value={document.submissionMode}
                          options={optionsFrom(submissionModeLabels)}
                          disabled={readOnly}
                          hint={"In stages, each section names the stage it is submitted in."}
                          onChange={submissionMode => update(current => ({...current, submissionMode}))}/>
            {document.submissionMode === InformationRequestSubmissionMode.STAGED && (
                <ChoiceSelect id={"template-settings-stage-order-select"}
                              label={"Stage order"}
                              value={document.submissionStageOrdering}
                              options={optionsFrom(submissionStageOrderingLabels)}
                              disabled={readOnly}
                              onChange={(submissionStageOrdering: InformationRequestSubmissionStageOrdering) =>
                                  update(current => ({...current, submissionStageOrdering}))}/>
            )}
            <TextField id={"template-settings-purpose-input"}
                       label={"Fact reuse purpose"}
                       value={document.factReusePurposeKey ?? ""}
                       hint={"Reviewed answers can be offered again to a later request with the same purpose. Leave empty to keep answers to this request."}
                       disabled={readOnly}
                       onChange={factReusePurposeKey => update(current => ({...current, factReusePurposeKey}))}/>
        </div>
    );
};

export default TemplateSettingsPanel;
