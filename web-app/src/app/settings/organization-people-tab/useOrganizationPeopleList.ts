import {useEffect, useMemo, useState} from "react";
import {AppUserPublicDto} from "../../models/models.tsx";
import {OrganizationRoleName} from "../../../services/types/roles.ts";
import {
    OrganizationPeopleSortOption,
    OrganizationPeopleStatus
} from "./organization-people-toolbar/OrganizationPeopleToolbar.tsx";

const personName = (user: AppUserPublicDto) =>
    [user.person?.firstName, user.person?.lastName].filter(Boolean).join(" ");

export const useOrganizationPeopleList = (users: AppUserPublicDto[], pageSize: number) =>
{
    const [searchQuery, setSearchQueryValue] = useState("");
    const [statusFilters, setStatusFilters] = useState<Set<OrganizationPeopleStatus>>(new Set());
    const [roleFilters, setRoleFilters] = useState<Set<OrganizationRoleName>>(new Set());
    const [sortOption, setSortOptionValue] = useState<OrganizationPeopleSortOption>("NAME_ASC");
    const [currentPage, setCurrentPage] = useState(0);

    const filteredUsers = useMemo(() => users.filter(user =>
    {
        const query = searchQuery.trim().toLocaleLowerCase();
        const matchesQuery = !query
            || personName(user).toLocaleLowerCase().includes(query)
            || user.email.toLocaleLowerCase().includes(query);
        const userStatus: OrganizationPeopleStatus = user.isActive ? "ACTIVE" : "INACTIVE";
        const matchesStatus = statusFilters.size === 0 || statusFilters.has(userStatus);
        const matchesRole = roleFilters.size === 0
            || user.organizationRoles.some(role => roleFilters.has(role as OrganizationRoleName));
        return matchesQuery && matchesStatus && matchesRole;
    }).sort((left, right) =>
    {
        const direction = sortOption.endsWith("DESC") ? -1 : 1;
        if (sortOption.startsWith("EMAIL"))
        {
            return left.email.localeCompare(right.email, undefined, {sensitivity: "base"}) * direction;
        }
        if (sortOption.startsWith("STATUS"))
        {
            const statusDifference = Number(right.isActive) - Number(left.isActive);
            return (statusDifference || personName(left).localeCompare(personName(right))) * direction;
        }
        return personName(left).localeCompare(personName(right), undefined, {sensitivity: "base"}) * direction;
    }), [roleFilters, searchQuery, sortOption, statusFilters, users]);

    const totalPages = Math.max(1, Math.ceil(filteredUsers.length / pageSize));
    const visibleUsers = filteredUsers.slice(currentPage * pageSize, (currentPage + 1) * pageSize);
    const resetPage = <T,>(setter: (value: T) => void) => (value: T) =>
    {
        setter(value);
        setCurrentPage(0);
    };
    const toggleSetValue = <T,>(setter: (update: (previous: Set<T>) => Set<T>) => void) => (value: T) =>
    {
        setter(previous =>
        {
            const next = new Set(previous);
            if (next.has(value)) next.delete(value);
            else next.add(value);
            return next;
        });
        setCurrentPage(0);
    };

    useEffect(() =>
    {
        setCurrentPage(page => Math.min(page, totalPages - 1));
    }, [totalPages]);

    return {
        searchQuery,
        statusFilters,
        roleFilters,
        sortOption,
        currentPage,
        totalPages,
        totalItems: filteredUsers.length,
        visibleUsers,
        setSearchQuery: resetPage(setSearchQueryValue),
        toggleStatusFilter: toggleSetValue(setStatusFilters),
        toggleRoleFilter: toggleSetValue(setRoleFilters),
        setSortOption: resetPage(setSortOptionValue),
        setCurrentPage
    };
};
