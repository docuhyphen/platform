import {Checkbox, PopoverSurface, Text} from "@fluentui/react-components";
import {
    CommunicationPublicationFilter,
    CommunicationStatusFilter,
} from "./CommunicationsToolbarTypes";
import {useCommunicationsToolbarStyles} from "./CommunicationsToolbarStyles";

interface CommunicationsFilterPopoverProps
{
    availableTags: string[];
    selectedTags: ReadonlySet<string>;
    onTagToggle: (tag: string) => void;
    statusFilter: CommunicationStatusFilter;
    onStatusFilterChange: (value: CommunicationStatusFilter) => void;
    publicationFilter: CommunicationPublicationFilter;
    onPublicationFilterChange: (value: CommunicationPublicationFilter) => void;
    showPublicationFilter: boolean;
}

const toElementIdPart = (value: string) => value.replace(/[^a-zA-Z0-9_-]/g, "-");

const CommunicationsFilterPopover = (props: CommunicationsFilterPopoverProps) =>
{
    const styles = useCommunicationsToolbarStyles();

    return (
        <PopoverSurface
            id={"communications-filter-popover"}
            className={styles.filterPopover}
        >
            <div className={styles.filterSection}>
                <Text className={styles.filterSectionTitle}>Status</Text>
                <Checkbox
                    id={"communications-filter-active"}
                    label={"Active"}
                    checked={props.statusFilter === "ACTIVE"}
                    onChange={() => props.onStatusFilterChange(
                        props.statusFilter === "ACTIVE" ? "ALL" : "ACTIVE",
                    )}
                />
                <Checkbox
                    id={"communications-filter-inactive"}
                    label={"Inactive"}
                    checked={props.statusFilter === "INACTIVE"}
                    onChange={() => props.onStatusFilterChange(
                        props.statusFilter === "INACTIVE" ? "ALL" : "INACTIVE",
                    )}
                />
            </div>
            {props.showPublicationFilter && (
                <div className={styles.filterSection}>
                    <Text className={styles.filterSectionTitle}>Publication</Text>
                    <Checkbox
                        id={"communications-filter-published"}
                        label={"Published"}
                        checked={props.publicationFilter === "PUBLISHED"}
                        onChange={() => props.onPublicationFilterChange(
                            props.publicationFilter === "PUBLISHED" ? "ALL" : "PUBLISHED",
                        )}
                    />
                    <Checkbox
                        id={"communications-filter-draft"}
                        label={"Draft"}
                        checked={props.publicationFilter === "DRAFT"}
                        onChange={() => props.onPublicationFilterChange(
                            props.publicationFilter === "DRAFT" ? "ALL" : "DRAFT",
                        )}
                    />
                </div>
            )}
            <div className={styles.filterSection}>
                <Text className={styles.filterSectionTitle}>Tags</Text>
                <div className={styles.filterList}>
                    {props.availableTags.map(tag => (
                        <Checkbox
                            id={`communications-filter-tag-${toElementIdPart(tag)}`}
                            key={tag}
                            label={tag}
                            checked={props.selectedTags.has(tag)}
                            onChange={() => props.onTagToggle(tag)}
                        />
                    ))}
                    {props.availableTags.length === 0 && (
                        <Text
                            size={200}
                            className={styles.emptyText}
                        >
                            No tags
                        </Text>
                    )}
                </div>
            </div>
        </PopoverSurface>
    );
};

export default CommunicationsFilterPopover;
