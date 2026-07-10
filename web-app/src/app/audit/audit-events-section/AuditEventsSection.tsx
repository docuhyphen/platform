import {useEffect, useState} from "react";
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
import {AuditEventDto} from "../../models/models.tsx";
import {fetchOrganizationAuditEvents, fetchPlatformAuditEvents} from "../../../services/auditService.ts";
import {useAuditEventPage} from "../components/use-audit-event-page/useAuditEventPage.ts";
import AuditEventTable from "../components/audit-event-table/AuditEventTable.tsx";
import AuditEventDetail from "../components/audit-event-detail/AuditEventDetail.tsx";
import {auditCategoryMetaMap} from "../components/audit-category-badge/AuditCategoryBadgeStyles.tsx";
import {AuditScope} from "../auditScope.ts";
import {useAuditEventsSectionStyles} from "./AuditEventsSectionStyles.tsx";

interface AuditEventsSectionProps
{
    scope: AuditScope;
}

const categoryOptions = Object.keys(auditCategoryMetaMap);

/** Search/filter + paginated results section of the Audit workspace. */
const AuditEventsSection = (
    {
        scope,
    }: AuditEventsSectionProps
) =>
{
    const styles = useAuditEventsSectionStyles();
    const [categories, setCategories] = useState<string[]>([]);
    const [occurredAfter, setOccurredAfter] = useState<string>("");
    const [occurredBefore, setOccurredBefore] = useState<string>("");
    const [selectedEvent, setSelectedEvent] = useState<AuditEventDto | null>(null);

    const {items, loading, error, cursor, loadMore, reset} = useAuditEventPage((params) =>
        scope.kind === "organization"
            ? fetchOrganizationAuditEvents(scope.organizationId, {
                ...params,
                categories,
                occurredAfter: occurredAfter || undefined,
                occurredBefore: occurredBefore || undefined,
            })
            : fetchPlatformAuditEvents({...params, categories})
    );

    useEffect(() =>
    {
        reset();
        // Re-search whenever the filter selection changes; `reset` itself is stable per hook.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [categories, occurredAfter, occurredBefore, scope]);

    const onCategorySelect = (_event: SelectionEvents, data: OptionOnSelectData) =>
    {
        setCategories(data.selectedOptions);
    };

    const selectedCategoryTags = (
        <div id={"audit-events-selected-categories"} className={styles.selectedCategories}>
            {categories.length === 0
                ? <Text size={200}>All categories</Text>
                : categories.map((category) => (
                    <Tag key={category} id={`audit-events-category-tag-${category.toLowerCase()}`} size={"small"}>
                        {auditCategoryMetaMap[category]?.label ?? category}
                    </Tag>
                ))}
        </div>
    );

    return (
        <div id={"audit-events-section"} className={styles.container}>
            <div className={styles.filterPanel}>
                <div className={styles.filterField}>
                    <Label id={"audit-events-categories-label"} htmlFor={"audit-events-categories"}>
                        Categories
                    </Label>
                    <Dropdown
                        id={"audit-events-categories"}
                        multiselect
                        appearance={"outline"}
                        selectedOptions={categories}
                        onOptionSelect={onCategorySelect}
                        button={{children: selectedCategoryTags}}
                    >
                        {categoryOptions.map((category) => (
                            <Option key={category} id={`audit-events-category-option-${category.toLowerCase()}`} value={category}>
                                {auditCategoryMetaMap[category]?.label ?? category}
                            </Option>
                        ))}
                    </Dropdown>
                </div>

                <div className={styles.filterField}>
                    <Label id={"audit-events-occurred-after-label"} htmlFor={"audit-events-occurred-after"}>
                        Occurred after
                    </Label>
                    <Input
                        id={"audit-events-occurred-after"}
                        type={"date"}
                        value={occurredAfter}
                        onChange={(_event, data) => setOccurredAfter(data.value)}
                    />
                </div>

                <div className={styles.filterField}>
                    <Label id={"audit-events-occurred-before-label"} htmlFor={"audit-events-occurred-before"}>
                        Occurred before
                    </Label>
                    <Input
                        id={"audit-events-occurred-before"}
                        type={"date"}
                        value={occurredBefore}
                        onChange={(_event, data) => setOccurredBefore(data.value)}
                    />
                </div>
            </div>

            {error && <Text id={"audit-events-error"} className={styles.errorText}>{error}</Text>}

            <AuditEventTable
                items={items}
                nextCursor={cursor}
                onLoadMore={loadMore}
                onEventClick={setSelectedEvent}
                loading={loading}
            />

            <AuditEventDetail
                event={selectedEvent}
                open={selectedEvent !== null}
                onDismiss={() => setSelectedEvent(null)}
            />
        </div>
    );
};

export default AuditEventsSection;
