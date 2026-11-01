import {Button, Input, Tag} from '@fluentui/react-components';
import {AddIcon} from '../../../components/IconBundles.tsx';
import {useExchangeInitiationStyles} from '../../../exchange-initiation/ExchangeInitiationStyles.tsx';
import {useBlueprintTagEditorStyles} from './BlueprintTagEditorStyles.tsx';

interface BlueprintTagEditorProps
{
    tags: string[];
    tagInput: string;
    onTagInputChange: (value: string) => void;
    onAddTag: () => void;
    onRemoveTag: (tag: string) => void;
}

const BlueprintTagEditor = ({tags, tagInput, onTagInputChange, onAddTag, onRemoveTag}: BlueprintTagEditorProps) =>
{
    const styles = useExchangeInitiationStyles();
    const editorStyles = useBlueprintTagEditorStyles();

    return (
        <div id={"blueprint-editor-tag-input"}
             className={styles.tagInput}>
            {tags.map(tag => (
                <Tag
                    id={`blueprint-editor-tag-${tag}`}
                    key={tag}
                    size="small"
                    dismissible
                    shape={"circular"}
                    onClick={() => onRemoveTag(tag)}
                >{tag}</Tag>
            ))}
            <Input
                id={"input-blueprint-tag"}
                size="small"
                appearance="underline"
                placeholder="Add tag, press Enter"
                value={tagInput}
                onChange={(_, d) => onTagInputChange(d.value)}
                onKeyDown={e =>
                {
                    if (e.key === 'Enter')
                    {
                        e.preventDefault();
                        onAddTag();
                    }
                }}
                className={editorStyles.tagInputField}
            />
            <Button
                id={"button-blueprint-add-tag"}
                shape={"circular"}
                appearance="subtle"
                size="medium"
                icon={<AddIcon/>}
                onClick={onAddTag}
            />
        </div>
    );
};

export default BlueprintTagEditor;
