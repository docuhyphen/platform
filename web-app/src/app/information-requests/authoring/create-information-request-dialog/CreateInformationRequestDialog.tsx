import {Checkbox, Field, MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {InformationRequestDto} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
import OneOffRequestFields from "../one-off-request-fields/OneOffRequestFields.tsx";
import RequestSourceChoice from "../request-source-choice/RequestSourceChoice.tsx";
import {useCreateInformationRequest} from "./useCreateInformationRequest.ts";

const PLATFORM_COPY_HINT = "To start from a platform Template, copy it first in Settings, under Information Requests, Platform.";

const OTHER_WORKSPACE = "This Exchange belongs to another workspace. Its Templates, Blueprints, and Schemas are listed only " +
    "while you work in that workspace, so switch to it from the account menu, or write a one-off request here.";

const BLUEPRINTS_UNAVAILABLE = "The Blueprints to start from could not be listed. Templates and one-off requests are still " +
    "available.";

interface Props
{
    open: boolean;
    exchangeId: string;
    owner: InformationRequestOwnerFields | null;
    onDismiss: () => void;
    onCreated: (request: InformationRequestDto, decisionMakerFailure: string | null) => void;
}

const CreateInformationRequestDialogContent = ({exchangeId, owner, onDismiss, onCreated}: Omit<Props, "open">) =>
{
    const state = useCreateInformationRequest(exchangeId, owner, onCreated);
    const {templates, blueprints, blueprintsUnavailable, loaded, error, reload, otherWorkspace} = state.options;

    return (
        <EditorDialog id={"create-information-request-dialog"}
                      title={"New Information Request"}
                      confirmLabel={"Create"}
                      busy={state.busy}
                      confirmDisabled={!state.ready}
                      wide={state.source === "adhoc"}
                      onConfirm={() => void state.create()}
                      onDismiss={onDismiss}>
            <RequestSourceChoice value={state.source}
                                 disabled={state.busy}
                                 onChange={state.setSource}/>
            {error && (
                <InformationRequestLoadError id={"information-request-create-sources-load-error"}
                                             message={error}
                                             onRetry={() => void reload()}/>
            )}
            {otherWorkspace && (
                <MessageBar id={"create-information-request-other-workspace"}
                            intent={"info"}>
                    <MessageBarBody
                        id={"create-information-request-other-workspace-body"}>{OTHER_WORKSPACE}</MessageBarBody>
                </MessageBar>
            )}
            {!loaded && !error && (
                <Spinner id={"create-information-request-loading"}
                         size={"tiny"}
                         label={"Loading Templates and Blueprints"}/>
            )}
            {state.source === "template" && !otherWorkspace && (
                <ChoiceSelect id={"create-information-request-template"}
                              label={"Template"}
                              value={state.templateId}
                              placeholder={"Choose a Template"}
                              hint={loaded && templates.length === 0
                                  ? `No published Template is available yet. ${PLATFORM_COPY_HINT}`
                                  : PLATFORM_COPY_HINT}
                              disabled={state.busy}
                              options={templates.map(template => ({value: template.id, label: template.displayName}))}
                              onChange={state.setTemplateId}/>
            )}
            {state.source === "blueprint" && !otherWorkspace && blueprintsUnavailable && (
                <InformationRequestLoadError id={"information-request-create-blueprints-load-error"}
                                             message={BLUEPRINTS_UNAVAILABLE}
                                             onRetry={() => void reload()}/>
            )}
            {state.source === "blueprint" && !otherWorkspace && (
                <ChoiceSelect id={"create-information-request-blueprint"}
                              label={"Blueprint"}
                              value={state.blueprintId}
                              placeholder={"Choose a Blueprint"}
                              hint={loaded && blueprints.length === 0 && !blueprintsUnavailable
                                  ? "No Blueprint names a Template Version yet."
                                  : undefined}
                              disabled={state.busy}
                              options={blueprints.map(blueprint => ({value: blueprint.id, label: blueprint.name}))}
                              onChange={state.setBlueprintId}/>
            )}
            {state.source === "adhoc" && (
                <OneOffRequestFields name={state.name}
                                     disabled={state.busy}
                                     editor={state.editor}
                                     onNameChange={state.setName}/>
            )}
            <Field id={"create-information-request-completion-gate-field"}
                   hint={"When selected, the Exchange cannot end until this request finishes. When cleared, ending the Exchange offers to cancel it."}>
                <Checkbox id={"create-information-request-completion-gate"}
                          label={"Must finish before the Exchange can end"}
                          checked={state.gatesExchangeClosure}
                          disabled={state.busy}
                          onChange={(_, data) => state.setGatesExchangeClosure(data.checked === true)}/>
            </Field>
            {state.error && (
                <MessageBar id={"create-information-request-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"create-information-request-error-body"}>{state.error}</MessageBarBody>
                </MessageBar>
            )}
        </EditorDialog>
    );
};

const CreateInformationRequestDialog = ({open, ...props}: Props) =>
    open ? <CreateInformationRequestDialogContent {...props}/> : null;

export default CreateInformationRequestDialog;
