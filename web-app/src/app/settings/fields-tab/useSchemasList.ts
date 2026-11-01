import {useEffect, useMemo, useState} from 'react';
import {FieldLifecycleStatus, SchemaDefinitionDto} from '../../models/models';
import {listSchemas} from '../../../services/fieldsService';
import {SchemasList} from './fieldsListTypes';
import {filterSchemas, matchNamespaces, namespacesOf, pageOf, totalPagesOf} from './fieldsListFilters';
import {useFieldsListControls, usePageClamp} from './useFieldsListControls';

export const useSchemasList = (active: boolean): SchemasList =>
{
    const controls = useFieldsListControls();
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listSchemas()
            .then(data => setSchemas(data.filter(s => s.status !== FieldLifecycleStatus.RETIRED)))
            .catch(() => setError('Failed to load schemas'))
            .finally(() => setLoading(false));
    };

    useEffect(() =>
    {
        if (active) load();
    }, [active]);

    const namespaces = useMemo(() => namespacesOf(schemas), [schemas]);

    const filteredNamespaces = useMemo(() =>
        matchNamespaces(namespaces, controls.filterSearch),
    [namespaces, controls.filterSearch]);

    const filtered = useMemo(() =>
        filterSchemas(schemas, controls.search, controls.filterNamespaces, controls.sortOrder),
    [schemas, controls.search, controls.filterNamespaces, controls.sortOrder]);

    const totalPages = totalPagesOf(filtered.length);
    usePageClamp(controls.currentPage, totalPages, controls.setCurrentPage);

    return {
        controls,
        loading,
        error,
        namespaces,
        filteredNamespaces,
        filtered,
        visible: pageOf(filtered, controls.currentPage),
        totalPages,
        filtersActive: controls.filterNamespaces.size > 0,
        load,
        reset: controls.reset,
    };
};
