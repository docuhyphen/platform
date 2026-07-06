import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";
import {useOrganizationPeoplePaginationStyles} from "./OrganizationPeoplePaginationStyles.tsx";

interface OrganizationPeoplePaginationProps
{
    currentPage: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    onPageChange: (page: number) => void;
}

const OrganizationPeoplePagination = (
    {currentPage, totalPages, totalItems, pageSize, onPageChange}: OrganizationPeoplePaginationProps
) =>
{
    const styles = useOrganizationPeoplePaginationStyles();
    const firstItem = totalItems === 0 ? 0 : currentPage * pageSize + 1;
    const lastItem = Math.min((currentPage + 1) * pageSize, totalItems);

    return (
        <div
            id="organization-people-pagination"
            className={styles.container}
        >
            <Text id="organization-people-pagination-summary">
                {firstItem}-{lastItem} of {totalItems} people
            </Text>
            <div
                id="organization-people-pagination-controls"
                className={styles.controls}
            >
                <Tooltip content="First page" relationship="description">
                    <Button
                        id="organization-people-pagination-first"
                        icon={<FirstPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(0)}
                    />
                </Tooltip>
                <Tooltip content="Previous page" relationship="description">
                    <Button
                        id="organization-people-pagination-previous"
                        icon={<PreviousPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(currentPage - 1)}
                    />
                </Tooltip>
                <Text id="organization-people-pagination-page">
                    {currentPage + 1} / {totalPages}
                </Text>
                <Tooltip content="Next page" relationship="description">
                    <Button
                        id="organization-people-pagination-next"
                        icon={<NextPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(currentPage + 1)}
                    />
                </Tooltip>
                <Tooltip content="Last page" relationship="description">
                    <Button
                        id="organization-people-pagination-last"
                        icon={<LastPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(totalPages - 1)}
                    />
                </Tooltip>
            </div>
        </div>
    );
};

export default OrganizationPeoplePagination;
