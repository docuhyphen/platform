import {useEffect, useMemo, useState} from "react";
import {OrganizationGroupDetailedDto} from "../../models/models.tsx";
import type {
    OrganizationGroupsSortOption,
    OrganizationGroupsStatusFilter
} from "./organization-groups-toolbar/OrganizationGroupsToolbar.tsx";

const searchableMemberText = (group: OrganizationGroupDetailedDto) => (group.members ?? [])
    .flatMap(member => [
        member.user?.person?.firstName,
        member.user?.person?.lastName,
        member.user?.email
    ])
    .filter(Boolean)
    .join(" ")
    .toLocaleLowerCase();

export const useOrganizationGroupsList = (groups: OrganizationGroupDetailedDto[], pageSize: number) =>
{
    const [searchQuery, setSearchQueryValue] = useState("");
    const [statusFilter, setStatusFilterValue] = useState<OrganizationGroupsStatusFilter>("ALL");
    const [sortOption, setSortOptionValue] = useState<OrganizationGroupsSortOption>("NAME_ASC");
    const [currentPage, setCurrentPage] = useState(0);

    const filteredGroups = useMemo(() => groups.filter(group =>
    {
        const query = searchQuery.trim().toLocaleLowerCase();
        const matchesQuery = !query
            || group.name.toLocaleLowerCase().includes(query)
            || searchableMemberText(group).includes(query);
        const matchesStatus = statusFilter === "ALL"
            || (statusFilter === "ACTIVE" && group.isActive)
            || (statusFilter === "INACTIVE" && !group.isActive);
        return matchesQuery && matchesStatus;
    }).sort((left, right) =>
    {
        const direction = sortOption.endsWith("DESC") ? -1 : 1;
        if (sortOption.startsWith("MEMBERS"))
        {
            return ((left.members?.length ?? 0) - (right.members?.length ?? 0)) * direction;
        }
        if (sortOption.startsWith("STATUS"))
        {
            const statusDifference = Number(right.isActive) - Number(left.isActive);
            return (statusDifference || left.name.localeCompare(right.name)) * direction;
        }
        return left.name.localeCompare(right.name, undefined, {sensitivity: "base"}) * direction;
    }), [groups, searchQuery, sortOption, statusFilter]);

    const totalPages = Math.max(1, Math.ceil(filteredGroups.length / pageSize));
    const visibleGroups = filteredGroups.slice(currentPage * pageSize, (currentPage + 1) * pageSize);
    const resetPage = <T,>(setter: (value: T) => void) => (value: T) =>
    {
        setter(value);
        setCurrentPage(0);
    };

    useEffect(() =>
    {
        setCurrentPage(page => Math.min(page, totalPages - 1));
    }, [totalPages]);

    return {
        searchQuery,
        statusFilter,
        sortOption,
        currentPage,
        totalPages,
        totalItems: filteredGroups.length,
        visibleGroups,
        setSearchQuery: resetPage(setSearchQueryValue),
        setStatusFilter: resetPage(setStatusFilterValue),
        setSortOption: resetPage(setSortOptionValue),
        setCurrentPage
    };
};
