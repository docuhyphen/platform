import {Spinner} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {useOrganizationPeopleTabStyles} from "./OrganizationPeopleTabStyles.tsx";
import {fetchMyOrganizationUsers} from "../../../services/organizationApi.ts";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserPublicDto, OrgMemberCapacityResponse} from "../../models/models.tsx";
import {getOrgMemberCapacity} from "../../../services/authApi.ts";
import OrganizationPeopleTable from "./organization-people-table/OrganizationPeopleTable.tsx";
import OrganizationPeoplePagination from "./organization-people-pagination/OrganizationPeoplePagination.tsx";
import OrganizationPeopleCapacity from "./organization-people-capacity/OrganizationPeopleCapacity.tsx";
import OrganizationPeopleDialogs from "./organization-people-dialogs/OrganizationPeopleDialogs.tsx";
import OrganizationPeopleToolbar from "./organization-people-toolbar/OrganizationPeopleToolbar.tsx";
import {useOrganizationPeopleList} from "./useOrganizationPeopleList.ts";

const PAGE_SIZE = 20;

const OrganizationPeopleTab = () =>
{
    const styles = useOrganizationPeopleTabStyles();
    const {token, appUserPersonOrganization, appUser} = useAuth();
    const [users, setUsers] = useState<AppUserPublicDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
    const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
    const [selectedUser, setSelectedUser] = useState<AppUserPublicDto | null>(null);
    const [capacity, setCapacity] = useState<OrgMemberCapacityResponse | null>(null);
    const peopleList = useOrganizationPeopleList(users, PAGE_SIZE);

    const loadUsers = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setUsers(await fetchMyOrganizationUsers(token || undefined));
        }
        catch (error: unknown)
        {
            setError(error instanceof Error ? error.message : "Failed to load users");
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        loadUsers();
        if (appUserPersonOrganization?.id)
        {
            getOrgMemberCapacity(appUserPersonOrganization.id)
                .then(setCapacity)
                .catch(() => setCapacity(null));
        }
    }, []);

    const openEditDialog = (user: AppUserPublicDto) =>
    {
        setSelectedUser(user);
        setIsEditDialogOpen(true);
    };

    return (
        <div
            id="organization-people"
            className={styles.container}
        >
            {error && <div className={styles.error}>{error}</div>}
            <OrganizationPeopleCapacity capacity={capacity}/>
            <OrganizationPeopleToolbar
                searchQuery={peopleList.searchQuery}
                statusFilters={peopleList.statusFilters}
                roleFilters={peopleList.roleFilters}
                sortOption={peopleList.sortOption}
                onSearchChange={peopleList.setSearchQuery}
                onStatusToggle={peopleList.toggleStatusFilter}
                onRoleToggle={peopleList.toggleRoleFilter}
                onSortChange={peopleList.setSortOption}
                onAdd={() => setIsAddDialogOpen(true)}
            />
            <div
                id="organization-people-table-scroll"
                className={styles.tableScroll}
            >
                {loading
                    ? <div className={styles.loading}><Spinner label="Loading..." size="small"/></div>
                    : <OrganizationPeopleTable
                        users={peopleList.visibleUsers}
                        currentUserId={appUser?.id}
                        onEdit={openEditDialog}
                    />}
            </div>
            <div
                id="organization-people-pagination-footer"
                className={styles.paginationFooter}
            >
                <OrganizationPeoplePagination
                    currentPage={peopleList.currentPage}
                    totalPages={peopleList.totalPages}
                    totalItems={peopleList.totalItems}
                    pageSize={PAGE_SIZE}
                    onPageChange={peopleList.setCurrentPage}
                />
            </div>
            <OrganizationPeopleDialogs
                organizationId={appUserPersonOrganization?.id}
                selectedUser={selectedUser}
                isAddOpen={isAddDialogOpen}
                isEditOpen={isEditDialogOpen}
                onAddDismiss={() => setIsAddDialogOpen(false)}
                onEditDismiss={() => setIsEditDialogOpen(false)}
                onComplete={() =>
                {
                    setIsAddDialogOpen(false);
                    setIsEditDialogOpen(false);
                    loadUsers();
                }}
            />
        </div>
    );
};

export default OrganizationPeopleTab;
