import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles";
import {useCommunicationsPaginationStyles} from "./CommunicationsPaginationStyles";

interface CommunicationsPaginationProps
{
    currentPage: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    onPageChange: (page: number) => void;
}

const CommunicationsPagination = ({
    currentPage,
    totalPages,
    totalItems,
    pageSize,
    onPageChange,
}: CommunicationsPaginationProps) =>
{
    const styles = useCommunicationsPaginationStyles();
    const firstItem = totalItems === 0 ? 0 : currentPage * pageSize + 1;
    const lastItem = Math.min((currentPage + 1) * pageSize, totalItems);

    return (
        <div
            id={"communications-pagination"}
            className={styles.container}
        >
            <Text id={"communications-pagination-summary"}>
                {firstItem}-{lastItem} of {totalItems} communications
            </Text>
            <div
                id={"communications-pagination-actions"}
                className={styles.actions}
            >
                <Tooltip content={"First page"} relationship={"description"}>
                    <Button
                        id={"communications-pagination-first"}
                        icon={<FirstPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(0)}
                    />
                </Tooltip>
                <Tooltip content={"Previous page"} relationship={"description"}>
                    <Button
                        id={"communications-pagination-previous"}
                        icon={<PreviousPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(currentPage - 1)}
                    />
                </Tooltip>
                <Text
                    id={"communications-pagination-page"}
                    className={styles.pageIndicator}
                >
                    {currentPage + 1} / {totalPages}
                </Text>
                <Tooltip content={"Next page"} relationship={"description"}>
                    <Button
                        id={"communications-pagination-next"}
                        icon={<NextPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(currentPage + 1)}
                    />
                </Tooltip>
                <Tooltip content={"Last page"} relationship={"description"}>
                    <Button
                        id={"communications-pagination-last"}
                        icon={<LastPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(totalPages - 1)}
                    />
                </Tooltip>
            </div>
        </div>
    );
};

export default CommunicationsPagination;
