import {Button, Spinner, Text} from "@fluentui/react-components";
import {BlueprintScope} from "../../../models/models.tsx";
import ChoiceSelect from "../../../information-requests/shared/choice-select/ChoiceSelect.tsx";
import {useBlueprintTemplateChoices} from "./useBlueprintTemplateChoices.ts";
import {useBlueprintInformationRequestTabStyles} from "./BlueprintInformationRequestTabStyles.tsx";

interface Props
{
    scope: BlueprintScope;
    templateVersionId?: string;
    onChange: (templateVersionId: string | undefined) => void;
}

const BlueprintInformationRequestTab = ({scope, templateVersionId, onChange}: Props) =>
{
    const styles = useBlueprintInformationRequestTabStyles();
    const {choices, loaded} = useBlueprintTemplateChoices(scope);
    const pinned = choices.find(choice => choice.versionId === templateVersionId);

    return (
        <div id={"blueprint-information-request-tab"}
             className={styles.tab}>
            <Text id={"blueprint-information-request-explanation"}
                  className={styles.muted}>
                Exchanges created from this Blueprint can start an Information Request from one published Template Version.
                Changing it affects only Exchanges created afterwards.
            </Text>
            {!loaded && (
                <Spinner id={"blueprint-information-request-loading"}
                         size={"tiny"}
                         label={"Loading Templates"}/>
            )}
            {templateVersionId && (
                <div id={"blueprint-information-request-pinned"}
                     className={styles.pinned}>
                    <Text id={"blueprint-information-request-pinned-text"}>
                        {pinned
                            ? `Exchanges from this Blueprint start an Information Request from ${pinned.displayName}, version ${pinned.versionNumber}.`
                            : "Exchanges from this Blueprint start an Information Request from an earlier Template Version."}
                    </Text>
                    <Button id={"blueprint-information-request-remove"}
                            appearance={"subtle"}
                            shape={"circular"}
                            aria-label={"Remove the Information Request"}
                            onClick={() => onChange(undefined)}>
                        Remove
                    </Button>
                </div>
            )}
            <ChoiceSelect id={"blueprint-information-request-template"}
                          label={"Template"}
                          value={pinned?.templateId ?? ""}
                          placeholder={"Choose a Template"}
                          hint={loaded && choices.length === 0 ? "No published Template is available in this scope." : undefined}
                          options={choices.map(choice => ({value: choice.templateId, label: choice.displayName}))}
                          onChange={templateId => onChange(choices.find(choice => choice.templateId === templateId)?.versionId)}/>
        </div>
    );
};

export default BlueprintInformationRequestTab;
