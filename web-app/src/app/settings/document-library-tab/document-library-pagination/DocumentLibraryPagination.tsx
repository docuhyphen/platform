import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";
import {useDocumentLibraryPaginationStyles} from "./DocumentLibraryPaginationStyles.tsx";

interface DocumentLibraryPaginationProps
{
    currentPage: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    onPageChange: (page: number) => void;
}

const DocumentLibraryPagination = ({
    currentPage,
    totalPages,
    totalItems,
    pageSize,
    onPageChange,
}: DocumentLibraryPaginationProps) =>
{
    const styles = useDocumentLibraryPaginationStyles();
    const firstItem = totalItems === 0 ? 0 : currentPage * pageSize + 1;
    const lastItem = Math.min((currentPage + 1) * pageSize, totalItems);

    return (
        <div
            id={"document-library-pagination"}
            className={styles.container}
        >
            <Text id={"document-library-pagination-summary"}>
                {firstItem}-{lastItem} of {totalItems} documents
            </Text>
            <div
                id={"document-library-pagination-actions"}
                className={styles.actions}
            >
                <Tooltip content={"First page"} relationship={"description"}>
                    <Button
                        id={"document-library-pagination-first"}
                        icon={<FirstPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(0)}
                    />
                </Tooltip>
                <Tooltip content={"Previous page"} relationship={"description"}>
                    <Button
                        id={"document-library-pagination-previous"}
                        icon={<PreviousPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(currentPage - 1)}
                    />
                </Tooltip>
                <Text
                    id={"document-library-pagination-page"}
                    className={styles.pageIndicator}
                >
                    {currentPage + 1} / {totalPages}
                </Text>
                <Tooltip content={"Next page"} relationship={"description"}>
                    <Button
                        id={"document-library-pagination-next"}
                        icon={<NextPageIcon/>}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(currentPage + 1)}
                    />
                </Tooltip>
                <Tooltip content={"Last page"} relationship={"description"}>
                    <Button
                        id={"document-library-pagination-last"}
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

export default DocumentLibraryPagination;
