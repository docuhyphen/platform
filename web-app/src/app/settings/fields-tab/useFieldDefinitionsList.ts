import {useEffect, useMemo, useState} from 'react';
import {FieldDefinitionDto, FieldLifecycleStatus, FieldValueType} from '../../models/models';
import {listFieldDefinitions, retireFieldDefinition} from '../../../services/fieldsService';
import {FieldDefinitionsList} from './fieldsListTypes';
import {filterDefinitions, matchNamespaces, namespacesOf, pageOf, toggleSetItem, totalPagesOf} from './fieldsListFilters';
import {useFieldsListControls, usePageClamp} from './useFieldsListControls';

export const useFieldDefinitionsList = (active: boolean): FieldDefinitionsList =>
{
    const controls = useFieldsListControls();
    const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [filterTypes, setFilterTypes] = useState<Set<FieldValueType>>(new Set());

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listFieldDefinitions()
            .then(data => setDefinitions(data.filter(d => d.status !== FieldLifecycleStatus.RETIRED)))
            .catch(() => setError('Failed to load fields'))
            .finally(() => setLoading(false));
    };

    useEffect(() =>
    {
        if (active) load();
    }, [active]);

    const namespaces = useMemo(() => namespacesOf(definitions), [definitions]);

    const filteredNamespaces = useMemo(() =>
        matchNamespaces(namespaces, controls.filterSearch),
    [namespaces, controls.filterSearch]);

    const filtered = useMemo(() =>
        filterDefinitions(definitions, controls.search, controls.filterNamespaces, filterTypes, controls.sortOrder),
    [definitions, controls.search, controls.filterNamespaces, filterTypes, controls.sortOrder]);

    const totalPages = totalPagesOf(filtered.length);
    usePageClamp(controls.currentPage, totalPages, controls.setCurrentPage);

    const toggleType = (type: FieldValueType) =>
    {
        setFilterTypes(prev => toggleSetItem(prev, type));
        controls.setCurrentPage(0);
    };

    const reset = () =>
    {
        controls.reset();
        setFilterTypes(new Set());
    };

    const retire = async (definition: FieldDefinitionDto) =>
    {
        await retireFieldDefinition(definition.id).catch((): null => null);
        load();
    };

    return {
        controls,
        loading,
        error,
        namespaces,
        filteredNamespaces,
        filtered,
        visible: pageOf(filtered, controls.currentPage),
        totalPages,
        filtersActive: controls.filterNamespaces.size > 0 || filterTypes.size > 0,
        filterTypes,
        toggleType,
        load,
        reset,
        retire,
    };
};
