import {useEffect, useState} from "react";
import {FieldScopeKind, SchemaDefinitionDto, SchemaFieldBindingDto} from "../../../../models/models.tsx";
import {getResolvedSchema, listSchemas} from "../../../../../services/fieldsService.ts";

const EXCHANGE_RESOURCE = "EXCHANGE";

export const useApplicabilitySchemaFields = () =>
{
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [selectedSchemaId, setSelectedSchemaId] = useState<string>("");
    const [fields, setFields] = useState<SchemaFieldBindingDto[]>([]);
    const [loadingFields, setLoadingFields] = useState(false);

    useEffect(() =>
    {
        listSchemas()
            .then(all => setSchemas(all.filter(s =>
                s.scopeKind === FieldScopeKind.ORGANIZATION && s.targetResourceType === EXCHANGE_RESOURCE)))
            .catch(() => setSchemas([]));
    }, []);

    useEffect(() =>
    {
        if (!selectedSchemaId)
        {
            setFields([]);
            return;
        }
        setLoadingFields(true);
        getResolvedSchema(selectedSchemaId)
            .then(resolved => setFields(resolved.fields))
            .catch(() => setFields([]))
            .finally(() => setLoadingFields(false));
    }, [selectedSchemaId]);

    return {schemas, selectedSchemaId, setSelectedSchemaId, fields, loadingFields};
};
