import React from 'react';
import {Button, Tag, TagGroup} from '@fluentui/react-components';
import {useExchangeInitiationStyles} from '../../../ExchangeInitiationStyles.tsx';

interface BlueprintPickerActiveTagsProps
{
    selectedTags: Set<string>;
    onDismissTag: (tag: string) => void;
    onClearAll: () => void;
}

const BlueprintPickerActiveTags: React.FC<BlueprintPickerActiveTagsProps> = ({selectedTags, onDismissTag, onClearAll}) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <div id={"blueprint-picker-active-tags-row"}
             className={styles.blueprintPickerActiveTagsRow}>
            <TagGroup
                id={"blueprint-picker-map-tag-group"}
                onDismiss={(_ev, {value}) => onDismissTag(value)}
            >
                {[...selectedTags].map(tag => (
                    <Tag id={`blueprint-picker-tag-${tag}`}
                         key={tag}
                         value={tag}
                         size="small"
                         dismissible>
                        {tag}
                    </Tag>
                ))}
            </TagGroup>
            <Button
                id="blueprint-picker-clear-tags-btn"
                size="small"
                appearance="subtle"
                shape="circular"
                onClick={onClearAll}
            >
                Clear all
            </Button>
        </div>
    );
};

export default BlueprintPickerActiveTags;
