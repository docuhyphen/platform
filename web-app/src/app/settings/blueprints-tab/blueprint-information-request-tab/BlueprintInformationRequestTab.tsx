import {Badge, Button, Spinner, Text} from "@fluentui/react-components";
import {BlueprintInformationRequestTemplateVersionDto, BlueprintScope} from "../../../models/models.tsx";
import ChoiceSelect from "../../../information-requests/shared/choice-select/ChoiceSelect.tsx";
import InformationRequestLoadError
    from "../../../information-requests/shared/load-error/InformationRequestLoadError.tsx";
import BlueprintTemplateVersionNotice from "./blueprint-template-version-notice/BlueprintTemplateVersionNotice.tsx";
import {pinnedVersionState} from "./blueprintPinnedVersion.ts";
import {useBlueprintTemplateChoices} from "./useBlueprintTemplateChoices.ts";
import {useBlueprintInformationRequestTabStyles} from "./BlueprintInformationRequestTabStyles.tsx";

interface Props
{
    scope: BlueprintScope;
    templateVersionId?: string;
    pinnedVersion?: BlueprintInformationRequestTemplateVersionDto;
    onChange: (templateVersionId: string | undefined) => void;
}

const pinnedText = (name?: string, versionNumber?: number): string => name && versionNumber !== undefined
    ? `Exchanges started from this Blueprint create a draft Information Request from ${name}, version ${versionNumber}.`
    : "Exchanges started from this Blueprint create a draft Information Request from an earlier Template Version.";

const BlueprintInformationRequestTab = ({scope, templateVersionId, pinnedVersion, onChange}: Props) =>
{
    const styles = useBlueprintInformationRequestTabStyles();
    const {choices, loaded, error, reload, available} = useBlueprintTemplateChoices(scope);
    const pinned = choices.find(choice => choice.versionId === templateVersionId);
    const state = pinnedVersion ? pinnedVersionState(pinnedVersion) : null;

    return (
        <div id={"blueprint-information-request-tab"}
             className={styles.tab}>
            {available
                ? (
                    <Text id={"blueprint-information-request-explanation"}
                          className={styles.muted}>
                        Starting an Exchange from this Blueprint creates a draft Information Request from one published
                        Template Version,
                        with the person who starts it as Decision Maker. Exchange documents become optional because the
                        request collects them.
                        Changing it affects only Exchanges started afterwards.
                    </Text>
                )
                : (
                    <Text id={"blueprint-information-request-unavailable"}>
                        A platform Blueprint cannot start an Information Request, because it has no owner whose
                        Templates the
                        request could use. Choose a Template on an organization or personal Blueprint instead.
                    </Text>
                )}
            {!loaded && (
                <Spinner id={"blueprint-information-request-loading"}
                         size={"tiny"}
                         label={"Loading Templates"}/>
            )}
            {error && (
                <InformationRequestLoadError id={"blueprint-information-request-templates-error"}
                                             message={error}
                                             onRetry={() => void reload()}/>
            )}
            {templateVersionId && (
                <div id={"blueprint-information-request-pinned"}
                     className={styles.pinned}>
                    <Text id={"blueprint-information-request-pinned-text"}>
                        {pinned
                            ? pinnedText(pinned.displayName, pinned.versionNumber)
                            : pinnedText(pinnedVersion?.templateDisplayName, pinnedVersion?.versionNumber)}
                    </Text>
                    {state && (
                        <Badge id={"blueprint-information-request-pinned-state"}
                               appearance={"tint"}
                               color={state === "Retired" ? "danger" : "warning"}>
                            {state}
                        </Badge>
                    )}
                    <Button id={"blueprint-information-request-remove"}
                            appearance={"subtle"}
                            shape={"circular"}
                            aria-label={"Remove the Information Request"}
                            onClick={() => onChange(undefined)}>
                        Remove
                    </Button>
                </div>
            )}
            {templateVersionId && pinnedVersion && <BlueprintTemplateVersionNotice pinnedVersion={pinnedVersion}/>}
            {available && (
                <>
                    <ChoiceSelect id={"blueprint-information-request-template"}
                                  label={"Template"}
                                  value={pinned?.templateId ?? ""}
                                  placeholder={"Choose a Template"}
                                  hint={loaded && !error && choices.length === 0 ? "No published Template is available in this workspace." : undefined}
                                  options={choices.map(choice => ({
                                      value: choice.templateId,
                                      label: choice.displayName
                                  }))}
                                  onChange={templateId => onChange(choices.find(choice => choice.templateId === templateId)?.versionId)}/>
                    <Text id={"blueprint-information-request-copy-hint"}
                          size={200}
                          className={styles.muted}>
                        Only this workspace&apos;s own published Templates are listed. To use a platform Template, copy
                        it with
                        Copy as a new Template in Settings, Content - Information Requests, then publish the copy.
                    </Text>
                </>
            )}
        </div>
    );
};

export default BlueprintInformationRequestTab;
