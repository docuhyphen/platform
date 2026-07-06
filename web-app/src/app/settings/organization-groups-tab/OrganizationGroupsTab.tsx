import {Spinner} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {fetchMyOrganizationGroups} from "../../../services/organizationApi.ts";
import {OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../models/models.tsx";
import {useOrganizationGroupTabStyles} from "./OrganizationGroupsTabStyles.tsx";
import OrganizationGroupsToolbar from "./organization-groups-toolbar/OrganizationGroupsToolbar.tsx";
import OrganizationGroupsTable from "./organization-groups-table/OrganizationGroupsTable.tsx";
import OrganizationGroupsPagination from "./organization-groups-pagination/OrganizationGroupsPagination.tsx";
import OrganizationGroupsDialogs from "./organization-groups-dialogs/OrganizationGroupsDialogs.tsx";
import {useOrganizationGroupsList} from "./useOrganizationGroupsList.ts";

interface OrganizationGroupsTabProps
{
    appUserPersonOrganization: OrganizationDetailedDto;
}

const PAGE_SIZE = 20;

const OrganizationGroupsTab = ({appUserPersonOrganization}: OrganizationGroupsTabProps) =>
{
    const styles = useOrganizationGroupTabStyles();
    const {token} = useAuth();
    const [groups, setGroups] = useState<OrganizationGroupDetailedDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isAddOpen, setIsAddOpen] = useState(false);
    const [isEditOpen, setIsEditOpen] = useState(false);
    const [isDeleteOpen, setIsDeleteOpen] = useState(false);
    const [selectedGroup, setSelectedGroup] = useState<OrganizationGroupDetailedDto | null>(null);
    const groupList = useOrganizationGroupsList(groups, PAGE_SIZE);

    const loadGroups = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setGroups(await fetchMyOrganizationGroups(token || undefined));
        }
        catch (error: unknown)
        {
            setError(error instanceof Error ? error.message : "Failed to load groups");
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        loadGroups();
    }, [appUserPersonOrganization.id]);

    const openEdit = (group: OrganizationGroupDetailedDto) =>
    {
        setSelectedGroup(group);
        setIsEditOpen(true);
    };

    const openDelete = (group: OrganizationGroupDetailedDto) =>
    {
        setSelectedGroup(group);
        setIsDeleteOpen(true);
    };

    const closeDialogsAndReload = () =>
    {
        setIsAddOpen(false);
        setIsEditOpen(false);
        setIsDeleteOpen(false);
        setSelectedGroup(null);
        loadGroups();
    };

    return (
        <div
            id="organization-groups"
            className={styles.container}
        >
            {error && <div className={styles.error}>{error}</div>}
            <OrganizationGroupsToolbar
                searchQuery={groupList.searchQuery}
                statusFilter={groupList.statusFilter}
                sortOption={groupList.sortOption}
                onSearchChange={groupList.setSearchQuery}
                onStatusChange={groupList.setStatusFilter}
                onSortChange={groupList.setSortOption}
                onCreate={() => setIsAddOpen(true)}
            />
            <div
                id="organization-groups-table-scroll"
                className={styles.tableScroll}
            >
                {loading
                    ? <div className={styles.loading}><Spinner label="Loading..." size="small"/></div>
                    : <OrganizationGroupsTable
                        groups={groupList.visibleGroups}
                        onEdit={openEdit}
                        onDelete={openDelete}
                    />}
            </div>
            <div
                id="organization-groups-pagination-footer"
                className={styles.paginationFooter}
            >
                <OrganizationGroupsPagination
                    currentPage={groupList.currentPage}
                    totalPages={groupList.totalPages}
                    totalItems={groupList.totalItems}
                    pageSize={PAGE_SIZE}
                    onPageChange={groupList.setCurrentPage}
                />
            </div>
            <OrganizationGroupsDialogs
                organizationId={appUserPersonOrganization.id ?? ""}
                organization={appUserPersonOrganization}
                selectedGroup={selectedGroup}
                isAddOpen={isAddOpen}
                isEditOpen={isEditOpen}
                isDeleteOpen={isDeleteOpen}
                onAddDismiss={() => setIsAddOpen(false)}
                onEditDismiss={() => setIsEditOpen(false)}
                onDeleteDismiss={() => setIsDeleteOpen(false)}
                onComplete={closeDialogsAndReload}
            />
        </div>
    );
};

export default OrganizationGroupsTab;
