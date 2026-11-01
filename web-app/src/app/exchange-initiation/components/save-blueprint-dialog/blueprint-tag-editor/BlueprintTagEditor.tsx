import React, {useState} from 'react';
import {Button, Input, Tag} from '@fluentui/react-components';
import {AddIcon} from '../../../../components/IconBundles.tsx';
import {useBlueprintTagEditorStyles} from './BlueprintTagEditorStyles.tsx';

interface BlueprintTagEditorProps
{
    tags: string[];
    onAddTag: (tag: string) => void;
    onRemoveTag: (tag: string) => void;
}

const BlueprintTagEditor: React.FC<BlueprintTagEditorProps> = ({tags, onAddTag, onRemoveTag}) =>
{
    const styles = useBlueprintTagEditorStyles();
    const [tagInput, setTagInput] = useState('');

    const addTag = () =>
    {
        onAddTag(tagInput);
        setTagInput('');
    };

    return (
        <div id={"save-blueprint-dialog-div-2"}
             className={styles.tagInput}>
            {tags.map(tag => (
                <Tag
                    id={`save-blueprint-dialog-tag-1-${tag}`}
                    key={tag}
                    size="small"
                    dismissible
                    onClick={() => onRemoveTag(tag)}
                >
                    {tag}
                </Tag>
            ))}
            <Input
                id={"save-blueprint-tag-input"}
                size="small"
                appearance="underline"
                placeholder="Add tag, press Enter"
                value={tagInput}
                onChange={(_, d) => setTagInput(d.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
            />
            <Button
                id={"save-blueprint-add-tag-btn"}
                shape="circular"
                appearance="subtle"
                size="medium"
                icon={<AddIcon/>}
                onClick={addTag}
            />
        </div>
    );
};

export default BlueprintTagEditor;
