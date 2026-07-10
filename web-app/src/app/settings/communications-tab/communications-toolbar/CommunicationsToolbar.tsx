import {
    Button,
    Field,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Popover,
    PopoverTrigger,
    SearchBox,
    Tooltip,
} from "@fluentui/react-components";
import {CheckmarkIcon, FilterIcon, SortDownIcon, SortUpIcon} from "../../../components/IconBundles";
import ViewModeToggle from "../../../components/ViewModeToggle";
import CommunicationsFilterPopover from "./CommunicationsFilterPopover";
import {
    CommunicationsToolbarProps,
    CommunicationSortOrder,
} from "./CommunicationsToolbarTypes";
import {useCommunicationsToolbarStyles} from "./CommunicationsToolbarStyles";

export type {
    CommunicationPublicationFilter,
    CommunicationSortOrder,
    CommunicationStatusFilter,
} from "./CommunicationsToolbarTypes";

const sortLabels: Record<CommunicationSortOrder, string> = {
    updatedDesc: "Recently updated",
    nameAsc: "Name (A-Z)",
    nameDesc: "Name (Z-A)",
    subjectAsc: "Subject (A-Z)",
};

const CommunicationsToolbar = (props: CommunicationsToolbarProps) =>
{
    const styles = useCommunicationsToolbarStyles();
    const filtersActive = props.selectedTags.size > 0 ||
        props.statusFilter !== "ALL" ||
        props.publicationFilter !== "ALL";

    return (
        <div
            id={"communications-toolbar"}
            className={styles.container}
        >
            <div
                id={"communications-toolbar-left"}
                className={styles.leftControls}
            >
                <Field
                    id={"communications-search-field"}
                    className={styles.searchField}
                >
                    <SearchBox
                        id={"communications-search"}
                        placeholder={"Search communications"}
                        maxLength={100}
                        value={props.searchQuery}
                        onChange={(_, data) => props.onSearchChange(data.value)}
                    />
                </Field>
                <Popover positioning={"below-end"}>
                    <PopoverTrigger disableButtonEnhancement>
                        <Tooltip content={"Filter communications"} relationship={"description"}>
                            <Button
                                id={"communications-filter"}
                                icon={<FilterIcon/>}
                                appearance={filtersActive ? "primary" : "subtle"}
                                shape={"circular"}
                            />
                        </Tooltip>
                    </PopoverTrigger>
                    <CommunicationsFilterPopover
                        availableTags={props.availableTags}
                        selectedTags={props.selectedTags}
                        onTagToggle={props.onTagToggle}
                        statusFilter={props.statusFilter}
                        onStatusFilterChange={props.onStatusFilterChange}
                        publicationFilter={props.publicationFilter}
                        onPublicationFilterChange={props.onPublicationFilterChange}
                        showPublicationFilter={props.showPublicationFilter}
                    />
                </Popover>
                <Menu>
                    <MenuTrigger>
                        <Tooltip content={sortLabels[props.sortOrder]} relationship={"description"}>
                            <Button
                                id={"communications-sort"}
                                icon={props.sortOrder === "nameDesc" ? <SortDownIcon/> : <SortUpIcon/>}
                                appearance={props.sortOrder !== "updatedDesc" ? "primary" : "subtle"}
                                shape={"circular"}
                            />
                        </Tooltip>
                    </MenuTrigger>
                    <MenuPopover>
                        <MenuList>
                            {(Object.keys(sortLabels) as CommunicationSortOrder[]).map(option => (
                                <MenuItem
                                    id={`communications-sort-${option}`}
                                    key={option}
                                    icon={props.sortOrder === option ? <CheckmarkIcon/> : undefined}
                                    onClick={() => props.onSortOrderChange(option)}
                                >
                                    {sortLabels[option]}
                                </MenuItem>
                            ))}
                        </MenuList>
                    </MenuPopover>
                </Menu>
            </div>
            <ViewModeToggle
                value={props.viewMode}
                onChange={props.onViewModeChange}
            />
        </div>
    );
};

export default CommunicationsToolbar;
