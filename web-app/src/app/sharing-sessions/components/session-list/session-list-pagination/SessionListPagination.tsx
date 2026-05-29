import React from 'react';
import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../../components/IconBundles.tsx";
import {useSessionListPaginationStyles} from "./SessionListPaginationStyles.tsx";

interface SessionListPaginationProps
{
    currentPage: number;
    totalPages: number;
    onPageChange: (page: number) => void;
}

const SessionListPagination: React.FC<SessionListPaginationProps> = (
    {
        currentPage,
        totalPages,
        onPageChange
    }) =>
{
    const styles = useSessionListPaginationStyles();

    if (totalPages <= 1)
    {
        return null;
    }

    return (
        <div className={styles.container}>
            <Tooltip
                content={"First page"}
                relationship={"description"}>
                <Button
                    id="session-list-pagination-first"
                    icon={<FirstPageIcon/>}
                    appearance="subtle"
                    disabled={currentPage === 0}
                    onClick={() => onPageChange(0)}
                />
            </Tooltip>

            <Tooltip
                content={"Previous page"}
                relationship={"description"}>
                <Button
                    id="session-list-pagination-previous"
                    icon={<PreviousPageIcon/>}
                    appearance="subtle"
                    disabled={currentPage === 0}
                    onClick={() => onPageChange(currentPage - 1)}/>
            </Tooltip>

            <Text>
                {currentPage + 1} / {totalPages}
            </Text>

            <Tooltip
                content={"Next page"}
                relationship={"description"}>
                <Button
                    id="session-list-pagination-next"
                    icon={<NextPageIcon/>}
                    appearance="subtle"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => onPageChange(currentPage + 1)}/>
            </Tooltip>

            <Tooltip
                content={"Last page"}
                relationship={"description"}>
                <Button
                    id="session-list-pagination-last"
                    icon={<LastPageIcon/>}
                    appearance="subtle"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => onPageChange(totalPages - 1)}/>
            </Tooltip>
        </div>
    );
};

export default SessionListPagination;