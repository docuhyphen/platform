import {Button, Field, Input, Tag} from '@fluentui/react-components';
import {AddIcon} from '../../../../components/IconBundles.tsx';
import {useCommunicationTagEditorStyles} from './CommunicationTagEditorStyles';

interface Props
{
    tags: string[];
    tagInput: string;
    onTagInputChange: (value: string) => void;
    onAddTag: () => void;
    onRemoveTag: (tag: string) => void;
}

const CommunicationTagEditor = ({tags, tagInput, onTagInputChange, onAddTag, onRemoveTag}: Props) =>
{
    const styles = useCommunicationTagEditorStyles();

    return (
        <Field id={"communication-editor-dialog-field-5"}
               label="Tags">
            <div id={"communication-editor-dialog-div-4"}
                 className={styles.tagInput}>
                {tags.map(tag => (
                    <Tag
                        id={`communication-editor-dialog-tag-1-${tag}`}
                        key={tag}
                        size="small"
                        shape={"circular"}
                        dismissible
                        onClick={() => onRemoveTag(tag)}
                    >{tag}</Tag>
                ))}
                <Input
                    id={"input-comm-tag"}
                    size="small"
                    appearance="underline"
                    placeholder="Add tag, press Enter"
                    value={tagInput}
                    onChange={(_, d) => onTagInputChange(d.value)}
                    onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); onAddTag(); } }}
                    className={styles.tagInputField}
                />
                <Button
                    id={"button-comm-add-tag"}
                    shape={"circular"}
                    appearance="subtle"
                    size="medium"
                    icon={<AddIcon/>}
                    onClick={onAddTag}
                />
            </div>
        </Field>
    );
};

export default CommunicationTagEditor;
