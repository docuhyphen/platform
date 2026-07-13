import {
    Dropdown,
    Input,
    Label,
    Option,
    OptionOnSelectData,
    SelectionEvents,
    Tag,
    Text,
} from "@fluentui/react-components";
import {auditCategoryMetaMap} from "../../components/audit-category-badge/AuditCategoryBadgeStyles.tsx";
import {useAuditEventsFiltersStyles} from "./AuditEventsFiltersStyles.tsx";

interface AuditEventsFiltersProps
{
    categories: string[];
    occurredAfter: string;
    occurredBefore: string;
    onCategoriesChange: (categories: string[]) => void;
    onOccurredAfterChange: (value: string) => void;
    onOccurredBeforeChange: (value: string) => void;
}

const categoryOptions = Object.keys(auditCategoryMetaMap);

const AuditEventsFilters = (props: AuditEventsFiltersProps) =>
{
    const styles = useAuditEventsFiltersStyles();
    const onCategorySelect = (_event: SelectionEvents, data: OptionOnSelectData) =>
    {
        props.onCategoriesChange(data.selectedOptions);
    };
    const selectedCategoryTags = (
        <div
            id={"audit-events-selected-categories"}
            className={styles.selectedCategories}
        >
            {props.categories.length === 0
                ? <Text size={200}>All categories</Text>
                : props.categories.map((category) => (
                    <Tag
                        key={category}
                        id={`audit-events-category-tag-${category.toLowerCase()}`}
                        size={"small"}
                    >
                        {auditCategoryMetaMap[category]?.label ?? category}
                    </Tag>
                ))}
        </div>
    );

    return (
        <div
            id={"audit-events-filter-panel"}
            className={styles.filterPanel}
        >
            <div className={styles.filterField}>
                <Label
                    id={"audit-events-categories-label"}
                    htmlFor={"audit-events-categories"}
                >Categories</Label>
                <Dropdown
                    id={"audit-events-categories"}
                    multiselect
                    appearance={"outline"}
                    selectedOptions={props.categories}
                    onOptionSelect={onCategorySelect}
                    button={{children: selectedCategoryTags}}
                >
                    {categoryOptions.map((category) => (
                        <Option
                            key={category}
                            id={`audit-events-category-option-${category.toLowerCase()}`}
                            value={category}
                        >
                            {auditCategoryMetaMap[category]?.label ?? category}
                        </Option>
                    ))}
                </Dropdown>
            </div>
            <div className={styles.filterField}>
                <Label
                    id={"audit-events-occurred-after-label"}
                    htmlFor={"audit-events-occurred-after"}
                >Occurred after</Label>
                <Input
                    id={"audit-events-occurred-after"}
                    type={"date"}
                    value={props.occurredAfter}
                    onChange={(_event, data) => props.onOccurredAfterChange(data.value)}
                />
            </div>
            <div className={styles.filterField}>
                <Label
                    id={"audit-events-occurred-before-label"}
                    htmlFor={"audit-events-occurred-before"}
                >Occurred before</Label>
                <Input
                    id={"audit-events-occurred-before"}
                    type={"date"}
                    value={props.occurredBefore}
                    onChange={(_event, data) => props.onOccurredBeforeChange(data.value)}
                />
            </div>
        </div>
    );
};

export default AuditEventsFilters;
