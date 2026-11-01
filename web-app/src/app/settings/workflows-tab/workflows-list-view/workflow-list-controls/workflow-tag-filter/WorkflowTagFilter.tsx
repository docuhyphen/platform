import {useState} from 'react';
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
import {FilterIcon} from '../../../../../components/IconBundles';
import {useWorkflowTagFilterStyles} from './WorkflowTagFilterStyles';

interface Props
{
    availableTags: string[];
    selectedTags: ReadonlySet<string>;
    onTagToggle: (tag: string) => void;
}

const WorkflowTagFilter = ({availableTags, selectedTags, onTagToggle}: Props) =>
{
    const styles = useWorkflowTagFilterStyles();
    const [search, setSearch] = useState('');
    const visibleTags = availableTags.filter(tag =>
        tag.toLowerCase().includes(search.trim().toLowerCase()),
    );

    return (
        <Popover
            positioning="below-end"
            onOpenChange={(_, {open}) =>
            {
                if (!open) setSearch('');
            }}
        >
            <PopoverTrigger disableButtonEnhancement>
                <Tooltip
                    content="Filter by tag"
                    relationship="description"
                >
                    <Button
                        id="workflow-list-filter"
                        icon={<FilterIcon/>}
                        appearance={selectedTags.size > 0 ? 'primary' : 'subtle'}
                        shape="circular"
                    />
                </Tooltip>
            </PopoverTrigger>
            <PopoverSurface id={"workflow-tag-filter-popover-surface-1"}
                            className={styles.filterPopover}>
                <SearchBox
                    id="workflow-list-filter-search"
                    placeholder="Search tags"
                    size="small"
                    value={search}
                    onChange={(_, data) => setSearch(data.value)}
                />
                <div
                    id="workflow-list-filter-options"
                    className={styles.filterList}
                >
                    {visibleTags.map(tag => (
                        <Checkbox
                            id={`workflow-list-filter-${tag}`}
                            key={tag}
                            label={tag}
                            checked={selectedTags.has(tag)}
                            onChange={() => onTagToggle(tag)}
                        />
                    ))}
                    {visibleTags.length === 0 && (
                        <Text id={"workflow-tag-filter-text-1"}
                              className={styles.emptyText}>No tags found</Text>
                    )}
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default WorkflowTagFilter;
