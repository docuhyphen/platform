import {Field, Input, Textarea} from '@fluentui/react-components';
import {BlueprintEditorForm} from '../../useBlueprintEditorForm.ts';
import BlueprintTagEditor from '../../blueprint-tag-editor/BlueprintTagEditor.tsx';

interface BlueprintDetailsSectionProps
{
    form: BlueprintEditorForm;
}

const BlueprintDetailsSection = ({form}: BlueprintDetailsSectionProps) =>
{
    const {values, update} = form;

    return (
        <>
            <Field id={"blueprint-editor-blueprint-name-field"}
                   label="Blueprint Name"
                   required>
                <Input
                    id={"input-blueprint-name"}
                    value={values.name}
                    onChange={(_, d) => update({name: d.value})}
                    placeholder="Name shown in the blueprint list"
                />
            </Field>
            <Field id={"blueprint-editor-blueprint-summary-field"}
                   label="Blueprint Summary">
                <Textarea
                    id={"textarea-blueprint-summary"}
                    value={values.summary}
                    onChange={(_, d) => update({summary: d.value})}
                    rows={2}
                    placeholder="Short description shown in the blueprint list"
                />
            </Field>
            <Field id={"blueprint-editor-tags-field"}
                   label="Tags">
                <BlueprintTagEditor tags={values.tags}
                                    tagInput={form.tagInput}
                                    onTagInputChange={form.setTagInput}
                                    onAddTag={form.addTag}
                                    onRemoveTag={form.removeTag}/>
            </Field>
        </>
    );
};

export default BlueprintDetailsSection;
