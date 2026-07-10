import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";

interface AuditEventTablePaginationProps
{
    currentPage: number;
    loadedPages: number;
    firstItem: number;
    lastItem: number;
    loadedItems: number;
    hasMoreItems: boolean;
    loading: boolean;
    onFirstPage: () => void;
    onPreviousPage: () => void;
    onNextPage: () => void;
    onLastLoadedPage: () => void;
    controlsClassName: string;
    pageClassName: string;
}

const AuditEventTablePagination = ({
    currentPage,
    loadedPages,
    firstItem,
    lastItem,
    loadedItems,
    hasMoreItems,
    loading,
    onFirstPage,
    onPreviousPage,
    onNextPage,
    onLastLoadedPage,
    controlsClassName,
    pageClassName,
}: AuditEventTablePaginationProps) =>
{
    const canGoBack = currentPage > 0;
    const canGoForward = currentPage < loadedPages - 1 || hasMoreItems;

    return (
        <>
            <Text id={"audit-event-table-pagination-summary"}>
                {firstItem}-{lastItem} of {loadedItems} events
            </Text>
            <div
                id={"audit-event-table-pagination-controls"}
                className={controlsClassName}
            >
                <Tooltip content={"First page"} relationship={"description"}>
                    <Button
                        id={"audit-event-table-pagination-first"}
                        icon={<FirstPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={!canGoBack || loading}
                        onClick={onFirstPage}
                    />
                </Tooltip>
                <Tooltip content={"Previous page"} relationship={"description"}>
                    <Button
                        id={"audit-event-table-pagination-previous"}
                        icon={<PreviousPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={!canGoBack || loading}
                        onClick={onPreviousPage}
                    />
                </Tooltip>
                <Text
                    id={"audit-event-table-pagination-page"}
                    className={pageClassName}
                >
                    {currentPage + 1} / {loadedPages}
                </Text>
                <Tooltip content={"Next page"} relationship={"description"}>
                    <Button
                        id={"audit-event-table-pagination-next"}
                        icon={<NextPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={!canGoForward || loading}
                        onClick={onNextPage}
                    />
                </Tooltip>
                <Tooltip content={"Last loaded page"} relationship={"description"}>
                    <Button
                        id={"audit-event-table-pagination-last"}
                        icon={<LastPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage >= loadedPages - 1 || loading}
                        onClick={onLastLoadedPage}
                    />
                </Tooltip>
            </div>
        </>
    );
};

export default AuditEventTablePagination;
