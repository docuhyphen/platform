import {Button, Field, Input, Tag} from '@fluentui/react-components';
import {AddIcon} from '../../../../components/IconBundles.tsx';
import {useDocumentLibraryTagEditorStyles} from './DocumentLibraryTagEditorStyles.tsx';

interface DocumentLibraryTagEditorProps
{
    tags: string[];
    tagInput: string;
    onTagInputChange: (value: string) => void;
    onAddTag: () => void;
    onRemoveTag: (tag: string) => void;
}

const DocumentLibraryTagEditor = ({tags, tagInput, onTagInputChange, onAddTag, onRemoveTag}: DocumentLibraryTagEditorProps) =>
{
    const styles = useDocumentLibraryTagEditorStyles();

    return (
        <Field id={"document-library-editor-dialog-field-4"}
               label="Tags">
            <div id={"document-library-editor-dialog-div-3"}
                 className={styles.tagInput}>
                {tags.map(tag => (
                    <Tag id={`document-library-editor-dialog-tag-1-${tag}`}
                         key={tag}
                         size="small"
                         dismissible
                         shape={"circular"}
                         onClick={() => onRemoveTag(tag)}>{tag}</Tag>
                ))}
                <Input id={"input-doc-editor-tag"}
                       size="small"
                       appearance="underline"
                       placeholder="Add tag, press Enter"
                       value={tagInput}
                       onChange={(_, d) => onTagInputChange(d.value)}
                       onKeyDown={(e) =>
                       {
                           if (e.key === 'Enter')
                           {
                               e.preventDefault();
                               onAddTag();
                           }
                       }}
                       className={styles.tagInputField}/>
                <Button id={"button-doc-editor-add-tag"}
                        shape={"circular"}
                        appearance="subtle"
                        size="medium"
                        icon={<AddIcon/>}
                        onClick={onAddTag}/>
            </div>
        </Field>
    );
};

export default DocumentLibraryTagEditor;
