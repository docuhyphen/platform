import {Spinner} from "@fluentui/react-components";
import AuditEventTablePagination from "./AuditEventTablePagination.tsx";

interface AuditEventTableFooterProps
{
    loading: boolean;
    currentPage: number;
    loadedPages: number;
    firstItem: number;
    lastItem: number;
    loadedItems: number;
    hasMoreItems: boolean;
    footerClassName: string;
    controlsClassName: string;
    pageClassName: string;
    onFirstPage: () => void;
    onPreviousPage: () => void;
    onNextPage: () => void;
    onLastLoadedPage: () => void;
}

const AuditEventTableFooter = ({
    loading,
    currentPage,
    loadedPages,
    firstItem,
    lastItem,
    loadedItems,
    hasMoreItems,
    footerClassName,
    controlsClassName,
    pageClassName,
    onFirstPage,
    onPreviousPage,
    onNextPage,
    onLastLoadedPage,
}: AuditEventTableFooterProps) =>
{
    if (loading)
    {
        return (
            <div
                id={"audit-event-table-loading"}
                className={footerClassName}>
                <Spinner
                    size={"small"}
                    label={"Loading audit events..."}
                    labelPosition={"after"}/>
            </div>
        );
    }

    return (
        <div
            id={"audit-event-table-pagination"}
            className={footerClassName}>
            <AuditEventTablePagination
                currentPage={currentPage}
                loadedPages={loadedPages}
                firstItem={firstItem}
                lastItem={lastItem}
                loadedItems={loadedItems}
                hasMoreItems={hasMoreItems}
                loading={loading}
                onFirstPage={onFirstPage}
                onPreviousPage={onPreviousPage}
                onNextPage={onNextPage}
                onLastLoadedPage={onLastLoadedPage}
                controlsClassName={controlsClassName}
                pageClassName={pageClassName}/>
        </div>
    );
};

export default AuditEventTableFooter;
