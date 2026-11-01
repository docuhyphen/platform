import {
    Button,
    Checkbox,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    SearchBox,
    Text,
    Tooltip,
} from '@fluentui/react-components';
import {FilterIcon} from '../../../../components/IconBundles.tsx';
import {useDocumentLibraryTagFilterStyles} from './DocumentLibraryTagFilterStyles.tsx';

interface DocumentLibraryTagFilterProps
{
    filterSearch: string;
    onFilterSearchChange: (value: string) => void;
    tagOptions: string[];
    selectedTags: Set<string>;
    onToggleTag: (tag: string) => void;
}

const DocumentLibraryTagFilter = ({
    filterSearch,
    onFilterSearchChange,
    tagOptions,
    selectedTags,
    onToggleTag,
}: DocumentLibraryTagFilterProps) =>
{
    const styles = useDocumentLibraryTagFilterStyles();

    return (
        <Popover positioning="below-end"
                 onOpenChange={(_, {open}) =>
                 {
                     if (!open) onFilterSearchChange('');
                 }}>
            <PopoverTrigger disableButtonEnhancement>
                <Tooltip content="Filter by tag"
                         relationship="description">
                    <Button id="doc-filter-btn"
                            icon={<FilterIcon/>}
                            appearance={selectedTags.size > 0 ? 'primary' : 'subtle'}
                            shape="circular"/>
                </Tooltip>
            </PopoverTrigger>
            <PopoverSurface id={"document-library-tab-popover-surface-1"}
                            className={styles.filterPopover}>
                <SearchBox id={"document-library-tab-search-box-1"}
                           placeholder="Search tags"
                           size="small"
                           value={filterSearch}
                           onChange={(_, d) => onFilterSearchChange(d.value)}/>
                <div id={"document-library-tab-div-5"}
                     className={styles.filterPopoverList}>
                    {tagOptions.map(tag => (
                        <Checkbox id={`document-library-tab-checkbox-1-${tag}`}
                                  key={tag}
                                  label={tag}
                                  checked={selectedTags.has(tag)}
                                  onChange={() => onToggleTag(tag)}/>
                    ))}
                    {tagOptions.length === 0 && (
                        <Text id={"document-library-tab-text-1"}
                              size={200}
                              className={styles.filterEmptyText}>
                            No tags found
                        </Text>
                    )}
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default DocumentLibraryTagFilter;
