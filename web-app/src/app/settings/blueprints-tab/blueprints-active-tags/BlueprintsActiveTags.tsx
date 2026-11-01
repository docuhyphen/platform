import {Button, Tag, TagGroup} from '@fluentui/react-components';
import {useBlueprintsActiveTagsStyles} from './BlueprintsActiveTagsStyles.tsx';

interface BlueprintsActiveTagsProps
{
    selectedTags: Set<string>;
    onRemoveTag: (tag: string) => void;
    onClearTags: () => void;
}

const BlueprintsActiveTags = ({selectedTags, onRemoveTag, onClearTags}: BlueprintsActiveTagsProps) =>
{
    const styles = useBlueprintsActiveTagsStyles();

    return (
        <div id={"blueprints-tab-div-7"}
             className={styles.activeTagsRow}>
            <TagGroup
                id={"blueprints-tab-tag-group-1"}
                onDismiss={(_ev, {value}) => onRemoveTag(value)}
            >
                {[...selectedTags].map(tag => (
                    <Tag id={`blueprints-tab-tag-1-${tag}`}
                         key={tag}
                         value={tag}
                         size="small"
                         dismissible>
                        {tag}
                    </Tag>
                ))}
            </TagGroup>
            <Button
                id="blueprint-clear-tags-btn"
                size="small"
                appearance="subtle"
                shape="circular"
                onClick={onClearTags}
            >
                Clear all
            </Button>
        </div>
    );
};

export default BlueprintsActiveTags;
