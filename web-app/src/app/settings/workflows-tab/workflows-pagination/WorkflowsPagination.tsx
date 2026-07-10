import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";
import {useWorkflowsPaginationStyles} from "./WorkflowsPaginationStyles.tsx";

interface WorkflowsPaginationProps
{
    currentPage: number;
    totalPages?: number;
    firstItem: number;
    lastItem: number;
    totalItems?: number;
    itemLabel: string;
    hasNextPage?: boolean;
    onPageChange: (page: number) => void;
}

const WorkflowsPagination = ({
    currentPage,
    totalPages,
    firstItem,
    lastItem,
    totalItems,
    itemLabel,
    hasNextPage,
    onPageChange,
}: WorkflowsPaginationProps) =>
{
    const styles = useWorkflowsPaginationStyles();
    const canGoBack = currentPage > 0;
    const canGoForward = totalPages === undefined
        ? !!hasNextPage
        : currentPage < totalPages - 1;

    return (
        <div
            id={"workflows-pagination"}
            className={styles.container}
        >
            <Text id={"workflows-pagination-summary"}>
                {totalItems === undefined
                    ? `${firstItem}-${lastItem} ${itemLabel}`
                    : `${firstItem}-${lastItem} of ${totalItems} ${itemLabel}`}
            </Text>
            <div
                id={"workflows-pagination-actions"}
                className={styles.actions}
            >
                <Tooltip content={"First page"} relationship={"description"}>
                    <Button
                        id={"workflows-pagination-first"}
                        icon={<FirstPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={!canGoBack}
                        onClick={() => onPageChange(0)}
                    />
                </Tooltip>
                <Tooltip content={"Previous page"} relationship={"description"}>
                    <Button
                        id={"workflows-pagination-previous"}
                        icon={<PreviousPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={!canGoBack}
                        onClick={() => onPageChange(currentPage - 1)}
                    />
                </Tooltip>
                <Text
                    id={"workflows-pagination-page"}
                    className={styles.pageIndicator}
                >
                    {totalPages === undefined
                        ? `Page ${currentPage + 1}`
                        : `${currentPage + 1} / ${totalPages}`}
                </Text>
                <Tooltip content={"Next page"} relationship={"description"}>
                    <Button
                        id={"workflows-pagination-next"}
                        icon={<NextPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={!canGoForward}
                        onClick={() => onPageChange(currentPage + 1)}
                    />
                </Tooltip>
                <Tooltip content={"Last page"} relationship={"description"}>
                    <Button
                        id={"workflows-pagination-last"}
                        icon={<LastPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={totalPages === undefined || currentPage >= totalPages - 1}
                        onClick={() => totalPages !== undefined && onPageChange(totalPages - 1)}
                    />
                </Tooltip>
            </div>
        </div>
    );
};

export default WorkflowsPagination;
