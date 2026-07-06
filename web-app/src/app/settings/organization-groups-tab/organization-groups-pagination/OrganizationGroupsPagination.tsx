import {Button, Text, Tooltip} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";
import {useOrganizationGroupsPaginationStyles} from "./OrganizationGroupsPaginationStyles.tsx";

interface OrganizationGroupsPaginationProps
{
    currentPage: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    onPageChange: (page: number) => void;
}

const OrganizationGroupsPagination = ({
    currentPage,
    totalPages,
    totalItems,
    pageSize,
    onPageChange
}: OrganizationGroupsPaginationProps) =>
{
    const styles = useOrganizationGroupsPaginationStyles();
    const firstItem = totalItems === 0 ? 0 : currentPage * pageSize + 1;
    const lastItem = Math.min((currentPage + 1) * pageSize, totalItems);

    return (
        <div
            id="organization-groups-pagination"
            className={styles.container}
        >
            <Text id="organization-groups-pagination-summary">
                {firstItem}-{lastItem} of {totalItems} groups
            </Text>
            <div
                id="organization-groups-pagination-controls"
                className={styles.controls}
            >
                <Tooltip content="First page" relationship="description">
                    <Button
                        id="organization-groups-pagination-first"
                        icon={<FirstPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(0)}
                    />
                </Tooltip>
                <Tooltip content="Previous page" relationship="description">
                    <Button
                        id="organization-groups-pagination-previous"
                        icon={<PreviousPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage === 0}
                        onClick={() => onPageChange(currentPage - 1)}
                    />
                </Tooltip>
                <Text id="organization-groups-pagination-page">{currentPage + 1} / {totalPages}</Text>
                <Tooltip content="Next page" relationship="description">
                    <Button
                        id="organization-groups-pagination-next"
                        icon={<NextPageIcon/>}
                        appearance="subtle"
                        shape="circular"
                        disabled={currentPage >= totalPages - 1}
                        onClick={() => onPageChange(currentPage + 1)}
                    />
                </Tooltip>
                <Tooltip content="Last page" relationship="description">
                    <Button
                        id="organization-groups-pagination-last"
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

export default OrganizationGroupsPagination;
