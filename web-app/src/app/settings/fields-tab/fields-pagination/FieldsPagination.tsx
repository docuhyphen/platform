import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles";
import {useFieldsPaginationStyles} from "./FieldsPaginationStyles";

interface FieldsPaginationProps
{
    currentPage: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    itemLabel: string;
    onPageChange: (page: number) => void;
}

const FieldsPagination = ({
    currentPage,
    totalPages,
    totalItems,
    pageSize,
    itemLabel,
    onPageChange,
}: FieldsPaginationProps) =>
{
    const styles = useFieldsPaginationStyles();
    const firstItem = totalItems === 0 ? 0 : currentPage * pageSize + 1;
    const lastItem = Math.min((currentPage + 1) * pageSize, totalItems);

    return (
        <div
            id={"fields-pagination"}
            className={styles.container}
        >
            <Text id={"fields-pagination-summary"}>
                {firstItem}-{lastItem} of {totalItems} {itemLabel}
            </Text>
            <div
                id={"fields-pagination-actions"}
                className={styles.actions}
            >
                <Tooltip content={"First page"} relationship={"description"}>
                    <Button
                        id={"fields-pagination-first"}
                        icon={<FirstPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(0)}
                    />
                </Tooltip>
                <Tooltip content={"Previous page"} relationship={"description"}>
                    <Button
                        id={"fields-pagination-previous"}
                        icon={<PreviousPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(currentPage - 1)}
                    />
                </Tooltip>
                <Text
                    id={"fields-pagination-page"}
                    className={styles.pageIndicator}
                >
                    {currentPage + 1} / {totalPages}
                </Text>
                <Tooltip content={"Next page"} relationship={"description"}>
                    <Button
                        id={"fields-pagination-next"}
                        icon={<NextPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(currentPage + 1)}
                    />
                </Tooltip>
                <Tooltip content={"Last page"} relationship={"description"}>
                    <Button
                        id={"fields-pagination-last"}
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

export default FieldsPagination;
