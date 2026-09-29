import TextField from "../../shared/text-field/TextField.tsx";
import TemplateDocumentEditor from "../../template-document/template-document-editor/TemplateDocumentEditor.tsx";
import TemplatePublishCheck from "../../template-document/template-publish-check/TemplatePublishCheck.tsx";
import {TemplateDocumentEditor as TemplateDocumentEditorState} from "../../template-document/useTemplateDocumentEditor.ts";
import {useOneOffRequestFieldsStyles} from "./OneOffRequestFieldsStyles.tsx";

interface Props
{
    name: string;
    disabled: boolean;
    editor: TemplateDocumentEditorState;
    onNameChange: (name: string) => void;
}

const OneOffRequestFields = ({name, disabled, editor, onNameChange}: Props) =>
{
    const styles = useOneOffRequestFieldsStyles();

    return (
        <div id={"create-information-request-one-off"}
             className={styles.fields}>
            <TextField id={"create-information-request-name"}
                       label={"Request name"}
                       hint={"Respondents see this name."}
                       value={name}
                       disabled={disabled}
                       maxLength={200}
                       onChange={onNameChange}/>
            <TemplatePublishCheck id={"create-information-request-check"}
                                  title={"Before creating"}
                                  problems={editor.problems}
                                  readyText={"This request can be created."}
                                  onShow={editor.goTo}/>
            <TemplateDocumentEditor editor={editor}/>
        </div>
    );
};

export default OneOffRequestFields;
