import {useState} from "react";
import {Button, Input, Tag} from "@fluentui/react-components";
import {AddIcon} from "../../../../../components/IconBundles.tsx";
import {useWorkflowTagEditorStyles} from "./WorkflowTagEditorStyles.tsx";

interface Props
{
    tags: string[];
    onChange: (tags: string[]) => void;
}

const WorkflowTagEditor = ({tags, onChange}: Props) =>
{
    const styles = useWorkflowTagEditorStyles();
    const [tagInput, setTagInput] = useState("");

    const addTag = () =>
    {
        const tag = tagInput.trim();
        if (tag && !tags.includes(tag))
            onChange([...tags, tag]);
        setTagInput("");
    };

    const removeTag = (tag: string) =>
        onChange(tags.filter(t => t !== tag));

    return (
        <div id={"workflow-metadata-form-div-6"}
             className={styles.tagInput}>
            {tags.map(tag => (
                <Tag id={`workflow-metadata-form-tag-1-${tag}`}
                     key={tag}
                     size="small"
                     shape={"circular"}
                     dismissible
                     onClick={() => removeTag(tag)}>
                    {tag}
                </Tag>
            ))}
            <Input
                id="workflow-designer-tag-input"
                size="small"
                appearance="underline"
                placeholder="Add tag, press Enter"
                value={tagInput}
                onChange={(_, d) => setTagInput(d.value)}
                onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
                className={styles.tagInputField}
            />
            <Button
                id="workflow-designer-add-tag-btn"
                shape="circular"
                appearance="subtle"
                size={"medium"}
                icon={<AddIcon/>}
                onClick={addTag}
            />
        </div>
    );
};

export default WorkflowTagEditor;
