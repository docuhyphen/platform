/**
 * Typed Axios wrappers for the configurable Fields and Business Schema engine.
 *
 * Covers:
 *   - Field type registry lookup
 *   - Field Definition + immutable Field Contract management (/fields/*)
 *   - Schema Definition draft/publish/version management (/schemas/*)
 *   - Exchange schema assignment + typed field values (/exchanges/{id}/schema, /fields)
 */
import apiClient from './apiClient';
import {
    FieldContractDto,
    FieldDataClassification,
    FieldDefinitionDto,
    FieldOption,
    FieldScopeKind,
    FieldTypeInfoDto,
    FieldValueType,
    ResolvedSchemaViewDto,
    SchemaAssignmentDto,
    SchemaCompatibility,
    SchemaDefinitionDto,
    SchemaVersionDto,
} from '../app/models/models';

// ── Request shapes ────────────────────────────────────────────────────────────

export interface FieldContractRequest
{
    valueType: FieldValueType;
    label: string;
    description?: string;
    helpText?: string;
    constraintsJson?: string;
    options?: FieldOption[];
    dataClassification?: FieldDataClassification;
    isSearchable?: boolean;
    isFilterable?: boolean;
    isSortable?: boolean;
    isReportable?: boolean;
}

export interface CreateFieldDefinitionRequest
{
    namespace: string;
    fieldKey: string;
    scopeKind?: FieldScopeKind;
    contract: FieldContractRequest;
}

export interface BindingRequest
{
    fieldContractId: string;
    displayOrder?: number;
    section?: string;
    isRequired?: boolean;
    isReadOnly?: boolean;
    defaultValueJson?: string;
    visibility?: FieldDataClassification;
}

export interface CreateSchemaRequest
{
    namespace: string;
    schemaKey: string;
    displayName: string;
    description?: string;
    scopeKind?: FieldScopeKind;
    bindings?: BindingRequest[];
}

export interface UpdateBindingsRequest
{
    bindings: BindingRequest[];
}

export interface PublishSchemaRequest
{
    compatibility?: SchemaCompatibility;
}

export interface FieldValueEntry
{
    fieldContractId: string;
    value: unknown;
}

export interface SetFieldValuesRequest
{
    values: FieldValueEntry[];
}

export interface AssignSchemaRequest
{
    schemaDefinitionId: string;
}

// ── Internal helper ───────────────────────────────────────────────────────────

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

// ── Field type registry ─────────────────────────────────────────────────────

export const listFieldTypes = (): Promise<FieldTypeInfoDto[]> =>
    executeRequest(() => apiClient.get('/fields/types'));

// ── Field Definitions ─────────────────────────────────────────────────────────

export const listFieldDefinitions = (): Promise<FieldDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/fields/definitions'));

export const getFieldDefinition = (id: string): Promise<FieldDefinitionDto> =>
    executeRequest(() => apiClient.get(`/fields/definitions/${id}`));

export const createFieldDefinition = (
    request: CreateFieldDefinitionRequest,
): Promise<FieldDefinitionDto> =>
    executeRequest(() => apiClient.post('/fields/definitions', request));

export const listFieldContracts = (id: string): Promise<FieldContractDto[]> =>
    executeRequest(() => apiClient.get(`/fields/definitions/${id}/contracts`));

export const addFieldContractVersion = (
    id: string,
    request: FieldContractRequest,
): Promise<FieldDefinitionDto> =>
    executeRequest(() => apiClient.post(`/fields/definitions/${id}/contracts`, request));

export const retireFieldDefinition = (id: string): Promise<FieldDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/fields/definitions/${id}/status`, {status: 'RETIRED'}));

// ── Schema Definitions ──────────────────────────────────────────────────────

export const listSchemas = (): Promise<SchemaDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/schemas/definitions'));

export const getSchema = (id: string): Promise<SchemaDefinitionDto> =>
    executeRequest(() => apiClient.get(`/schemas/definitions/${id}`));

export const getResolvedSchema = (id: string): Promise<ResolvedSchemaViewDto> =>
    executeRequest(() => apiClient.get(`/schemas/definitions/${id}/resolved`));

export const createSchema = (request: CreateSchemaRequest): Promise<SchemaDefinitionDto> =>
    executeRequest(() => apiClient.post('/schemas/definitions', request));

export const updateSchemaDraftBindings = (
    id: string,
    request: UpdateBindingsRequest,
): Promise<SchemaVersionDto> =>
    executeRequest(() => apiClient.put(`/schemas/definitions/${id}/draft/bindings`, request));

export const publishSchemaDraft = (
    id: string,
    request?: PublishSchemaRequest,
): Promise<SchemaDefinitionDto> =>
    executeRequest(() => apiClient.post(`/schemas/definitions/${id}/draft/publish`, request ?? {}));

export const createSchemaDraftVersion = (id: string): Promise<SchemaVersionDto> =>
    executeRequest(() => apiClient.post(`/schemas/definitions/${id}/versions`, {}));

export const retireSchema = (id: string): Promise<SchemaDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/schemas/definitions/${id}/status`, {status: 'RETIRED'}));

// ── Exchange schema assignment + values ─────────────────────────────────────

/**
 * Fetch the exchange's current schema assignment and resolved values.
 * Returns null when no schema is assigned (server replies 204 No Content).
 */
export const getExchangeSchema = async (
    exchangeId: string,
): Promise<SchemaAssignmentDto | null> =>
{
    try
    {
        const {data, status} = await apiClient.get<SchemaAssignmentDto>(
            `/exchanges/${exchangeId}/schema`,
        );
        return status === 204 ? null : data;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const assignExchangeSchema = (
    exchangeId: string,
    request: AssignSchemaRequest,
): Promise<SchemaAssignmentDto> =>
    executeRequest(() => apiClient.put(`/exchanges/${exchangeId}/schema`, request));

export const unassignExchangeSchema = (exchangeId: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/exchanges/${exchangeId}/schema`));

export const setExchangeFieldValues = (
    exchangeId: string,
    request: SetFieldValuesRequest,
): Promise<SchemaAssignmentDto> =>
    executeRequest(() => apiClient.put(`/exchanges/${exchangeId}/fields`, request));
