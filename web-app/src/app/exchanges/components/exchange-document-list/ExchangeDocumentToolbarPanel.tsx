import React from "react";
import {InputOnChangeData, mergeClasses, SearchBoxChangeEvent} from "@fluentui/react-components";
import ExchangeDocumentToolbar, {DocumentSortOption, DocumentStatusFilter} from "./ExchangeDocumentToolbar.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";

interface ExchangeDocumentToolbarPanelProps
{
    visible: boolean;
    totalCount: number;
    uploadedCount: number;
    activeFilter: DocumentStatusFilter;
    sortOption: DocumentSortOption;
    searchQuery: string;
    onSearchChange: (event: SearchBoxChangeEvent, data: InputOnChangeData) => void;
    onFilterChange: (filter: DocumentStatusFilter) => void;
    onSortChange: (sort: DocumentSortOption) => void;
}

const ExchangeDocumentToolbarPanel: React.FC<ExchangeDocumentToolbarPanelProps> = (props) =>
{
    const styles = useExchangeDocumentsListStyles();
    const [renderToolbar, setRenderToolbar] = React.useState(props.visible);
    const [closing, setClosing] = React.useState(false);
    const closeTimeoutRef = React.useRef<number | null>(null);

    React.useEffect(() => () => {
        if (closeTimeoutRef.current) window.clearTimeout(closeTimeoutRef.current);
    }, []);

    React.useEffect(() => {
        if (closeTimeoutRef.current) window.clearTimeout(closeTimeoutRef.current);
        if (props.visible) {
            setClosing(false);
            setRenderToolbar(true);
        } else if (renderToolbar) {
            setClosing(true);
            closeTimeoutRef.current = window.setTimeout(() => {
                setRenderToolbar(false);
                setClosing(false);
            }, 180);
        }
    }, [props.visible, renderToolbar]);

    if (!renderToolbar) return null;

    return (
        <div id="exchange-document-toolbar-wrapper"
             className={mergeClasses(styles.toolbarWrapper, closing ? styles.toolbarLeaving : styles.toolbarEntering)}>
            <ExchangeDocumentToolbar totalCount={props.totalCount}
                                     uploadedCount={props.uploadedCount}
                                     activeFilter={props.activeFilter}
                                     sortOption={props.sortOption}
                                     searchQuery={props.searchQuery}
                                     onSearchChange={props.onSearchChange}
                                     onFilterChange={props.onFilterChange}
                                     onSortChange={props.onSortChange}/>
        </div>
    );
};

export default ExchangeDocumentToolbarPanel;
