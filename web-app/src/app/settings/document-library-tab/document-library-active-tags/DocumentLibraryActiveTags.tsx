import {Button, Tag, TagGroup} from '@fluentui/react-components';
import {useDocumentLibraryActiveTagsStyles} from './DocumentLibraryActiveTagsStyles.tsx';

interface DocumentLibraryActiveTagsProps
{
    selectedTags: Set<string>;
    onRemoveTag: (tag: string) => void;
    onClearTags: () => void;
}

const DocumentLibraryActiveTags = ({selectedTags, onRemoveTag, onClearTags}: DocumentLibraryActiveTagsProps) =>
{
    const styles = useDocumentLibraryActiveTagsStyles();

    return (
        <div id={"document-library-tab-div-6"}
             className={styles.activeTagsRow}>
            <TagGroup id={"document-library-tab-tag-group-1"}
                      onDismiss={(_ev, {value}) => onRemoveTag(value)}>
                {[...selectedTags].map(tag => (
                    <Tag id={`document-library-tab-tag-1-${tag}`}
                         key={tag}
                         value={tag}
                         size="small"
                         dismissible>
                        {tag}
                    </Tag>
                ))}
            </TagGroup>
            <Button id="doc-clear-tags-btn"
                    size="small"
                    appearance="outline"
                    shape={"circular"}
                    onClick={onClearTags}>
                Clear all
            </Button>
        </div>
    );
};

export default DocumentLibraryActiveTags;
