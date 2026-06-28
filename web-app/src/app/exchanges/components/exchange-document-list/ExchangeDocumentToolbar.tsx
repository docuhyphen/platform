import React from "react";
import {
    InputOnChangeData,
    Menu,
    MenuButton,
    MenuItemRadio,
    MenuList,
    MenuPopover,
    MenuTrigger,
    SearchBox,
    SearchBoxChangeEvent,
    ToggleButton
} from "@fluentui/react-components";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";

export type DocumentStatusFilter = "all" | "not-uploaded" | "uploaded";
export type DocumentSortOption = "default" | "not-uploaded" | "recent" | "name" | "name-desc";

interface ExchangeDocumentToolbarProps {
    totalCount: number;
    uploadedCount: number;
    activeFilter: DocumentStatusFilter;
    sortOption: DocumentSortOption;
    onSearchChange: (event: SearchBoxChangeEvent, data: InputOnChangeData) => void;
    onFilterChange: (filter: DocumentStatusFilter) => void;
    onSortChange: (sort: DocumentSortOption) => void;
}

const sortLabels: Record<DocumentSortOption, string> = {
    default: "Default order",
    "not-uploaded": "Not uploaded first",
    recent: "Recently uploaded",
    name: "Name A-Z",
    "name-desc": "Name Z-A",
};

const ExchangeDocumentToolbar: React.FC<ExchangeDocumentToolbarProps> = (props) => {
    const styles = useExchangeDocumentsListStyles();
    const notUploadedCount = props.totalCount - props.uploadedCount;

    return (
        <div id="exchange-document-toolbar"
             className={styles.toolbar}>
            <SearchBox id="exchange-documents-filter-input"
                       className={styles.searchField}
                       placeholder="Search documents"
                       aria-label="Search documents"
                       onChange={props.onSearchChange}/>
            <div id="exchange-document-status-filters"
                 className={styles.filterGroup}
                 role="group"
                 aria-label="Filter documents by upload status">
                <ToggleButton id="exchange-documents-filter-all"
                              size="small"
                              shape="circular"
                              checked={props.activeFilter === "all"}
                              onClick={() => props.onFilterChange("all")}>
                    All ({props.totalCount})
                </ToggleButton>
                <ToggleButton id="exchange-documents-filter-not-uploaded"
                              size="small"
                              shape="circular"
                              checked={props.activeFilter === "not-uploaded"}
                              onClick={() => props.onFilterChange("not-uploaded")}>
                    Not uploaded ({notUploadedCount})
                </ToggleButton>
                <ToggleButton id="exchange-documents-filter-uploaded"
                              size="small"
                              shape="circular"
                              checked={props.activeFilter === "uploaded"}
                              onClick={() => props.onFilterChange("uploaded")}>
                    Uploaded ({props.uploadedCount})
                </ToggleButton>
            </div>
            <div id="exchange-document-toolbar-actions"
                 className={styles.toolbarActions}>
                <Menu checkedValues={{sort: [props.sortOption]}}
                      onCheckedValueChange={(_, data) => {
                          const selection = data.checkedItems[0] as DocumentSortOption | undefined;
                          if (selection) props.onSortChange(selection);
                      }}>
                    <MenuTrigger disableButtonEnhancement>
                        <MenuButton id="exchange-documents-sort"
                                    size="small"
                                    shape="circular"
                                    appearance="subtle">
                            Sort: {sortLabels[props.sortOption]}
                        </MenuButton>
                    </MenuTrigger>
                    <MenuPopover>
                        <MenuList id="exchange-documents-sort-menu">
                            <MenuItemRadio id="exchange-documents-sort-default"
                                           name="sort"
                                           value="default">
                                Default order
                            </MenuItemRadio>
                            <MenuItemRadio id="exchange-documents-sort-not-uploaded"
                                           name="sort"
                                           value="not-uploaded">
                                Not uploaded first
                            </MenuItemRadio>
                            <MenuItemRadio id="exchange-documents-sort-recent"
                                           name="sort"
                                           value="recent">
                                Recently uploaded
                            </MenuItemRadio>
                            <MenuItemRadio id="exchange-documents-sort-name"
                                           name="sort"
                                           value="name">
                                Name A-Z
                            </MenuItemRadio>
                            <MenuItemRadio id="exchange-documents-sort-name-desc"
                                           name="sort"
                                           value="name-desc">
                                Name Z-A
                            </MenuItemRadio>
                        </MenuList>
                    </MenuPopover>
                </Menu>
            </div>
        </div>
    );
};

export default ExchangeDocumentToolbar;
