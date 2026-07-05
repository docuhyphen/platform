import {
    Button,
    Field,
    SearchBox,
    Tag,
    TagGroup,
} from '@fluentui/react-components';
import ViewModeToggle from '../../../../components/ViewModeToggle';
import {ViewMode} from '../../../../models/models';
import WorkflowSortMenu, {WorkflowSortOrder} from './WorkflowSortMenu';
import WorkflowTagFilter from './WorkflowTagFilter';
import {useWorkflowListControlsStyles} from './WorkflowListControlsStyles';

export type {WorkflowSortOrder};

interface Props
{
    searchQuery: string;
    onSearchChange: (value: string) => void;
    availableTags: string[];
    selectedTags: ReadonlySet<string>;
    onTagToggle: (tag: string) => void;
    onClearTags: () => void;
    sortOrder: WorkflowSortOrder;
    onSortOrderChange: (value: WorkflowSortOrder) => void;
    viewMode: ViewMode;
    onViewModeChange: (value: ViewMode) => void;
}

const WorkflowListControls = (props: Props) =>
{
    const styles = useWorkflowListControlsStyles();

    return (
        <div
            id="workflow-list-controls"
            className={styles.root}
        >
            <div
                id="workflow-list-controls-row"
                className={styles.controlsRow}
            >
                <Field className={styles.searchField}>
                    <SearchBox
                        id="workflow-list-search"
                        placeholder="Search workflows"
                        maxLength={100}
                        value={props.searchQuery}
                        onChange={(_, data) => props.onSearchChange(data.value)}
                    />
                </Field>
                <WorkflowTagFilter
                    availableTags={props.availableTags}
                    selectedTags={props.selectedTags}
                    onTagToggle={props.onTagToggle}
                />
                <WorkflowSortMenu
                    value={props.sortOrder}
                    onChange={props.onSortOrderChange}
                />
                <ViewModeToggle
                    value={props.viewMode}
                    onChange={props.onViewModeChange}
                />
            </div>

            {props.selectedTags.size > 0 && (
                <div
                    id="workflow-list-active-tags"
                    className={styles.activeTagsRow}
                >
                    <TagGroup onDismiss={(_, {value}) => props.onTagToggle(value)}>
                        {[...props.selectedTags].map(tag => (
                            <Tag
                                key={tag}
                                value={tag}
                                size="small"
                                dismissible
                            >
                                {tag}
                            </Tag>
                        ))}
                    </TagGroup>
                    <Button
                        id="workflow-list-clear-tags"
                        size="small"
                        appearance="subtle"
                        shape="circular"
                        onClick={props.onClearTags}
                    >
                        Clear all
                    </Button>
                </div>
            )}
        </div>
    );
};

export default WorkflowListControls;
