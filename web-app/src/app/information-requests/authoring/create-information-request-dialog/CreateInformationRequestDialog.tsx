import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {InformationRequestDto} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import OneOffRequestFields from "../one-off-request-fields/OneOffRequestFields.tsx";
import RequestSourceChoice from "../request-source-choice/RequestSourceChoice.tsx";
import {useCreateInformationRequest} from "./useCreateInformationRequest.ts";

interface Props
{
    open: boolean;
    exchangeId: string;
    onDismiss: () => void;
    onCreated: (request: InformationRequestDto) => void;
}

const CreateInformationRequestDialogContent = ({exchangeId, onDismiss, onCreated}: Omit<Props, "open">) =>
{
    const state = useCreateInformationRequest(exchangeId, onCreated);
    const {templates, blueprints, loaded} = state.options;

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
            {!loaded && (
                <Spinner id={"create-information-request-loading"}
                         size={"tiny"}
                         label={"Loading Templates and Blueprints"}/>
            )}
            {state.source === "template" && (
                <ChoiceSelect id={"create-information-request-template"}
                              label={"Template"}
                              value={state.templateId}
                              placeholder={"Choose a Template"}
                              hint={loaded && templates.length === 0 ? "No published Template is available yet." : undefined}
                              disabled={state.busy}
                              options={templates.map(template => ({value: template.id, label: template.displayName}))}
                              onChange={state.setTemplateId}/>
            )}
            {state.source === "blueprint" && (
                <ChoiceSelect id={"create-information-request-blueprint"}
                              label={"Blueprint"}
                              value={state.blueprintId}
                              placeholder={"Choose a Blueprint"}
                              hint={loaded && blueprints.length === 0 ? "No Blueprint names a Template Version yet." : undefined}
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
            {state.error && (
                <MessageBar id={"create-information-request-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{state.error}</MessageBarBody>
                </MessageBar>
            )}
        </EditorDialog>
    );
};

const CreateInformationRequestDialog = ({open, ...props}: Props) =>
    open ? <CreateInformationRequestDialogContent {...props}/> : null;

export default CreateInformationRequestDialog;
