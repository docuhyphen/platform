/**
 * Typed Axios wrappers for the configurable Fields and Business Schema engine.
 *
 * Covers:
 *   - Field type registry lookup
 *   - Field Definition + immutable Field Contract management (/fields/*)
 *   - Schema Definition draft/publish/version management (/schemas/*)
 *   - Exchange schema assignment + typed field values (/exchanges/{id}/schema, /fields)
 */
import axios from 'axios';
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
    ResponseError,
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
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

// ── Field type registry ─────────────────────────────────────────────────────

export const listFieldTypes = (): Promise<FieldTypeInfoDto[]> =>
    executeRequest(() => apiClient.get('/fields/types'));

// ── Field Definitions ─────────────────────────────────────────────────────────

export interface ListFieldDefinitionsParams
{
    scopeKind?: FieldScopeKind;
}

export const listFieldDefinitions = (
    params?: ListFieldDefinitionsParams,
): Promise<FieldDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/fields/definitions', {params}));

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

export interface ListSchemasParams
{
    scopeKind?: FieldScopeKind;
}

export const listSchemas = (params?: ListSchemasParams): Promise<SchemaDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/schemas/definitions', {params}));

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
    catch (error: unknown)
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

/** The header a conditional save states the version of the values it read in. */
const IF_MATCH_HEADER = 'If-Match';

/** The validator standing for whichever version is current, which excludes no version. */
const ANY_VERSION = '*';

/** The machine code the server states when the version a save named is no longer the current one. */
const STALE_VERSION_CODE = 'FIELDS_PRECONDITION_STALE';

/**
 * Whether a refused save was refused because the stored values moved past the version it named.
 * Both the status and the machine code are required, so a precondition refused for any other reason
 * is not mistaken for one a caller can recover from by reloading.
 */
const namesAVersionThatMovedOn = (error: unknown): boolean =>
    axios.isAxiosError(error)
    && error.response?.status === 412
    && (error.response.data as ResponseError | undefined)?.reasonCode === STALE_VERSION_CODE;

/** A refusal as the server stated it, falling back to whatever the transport could say. */
const statedRefusal = (error: unknown): unknown =>
{
    if (axios.isAxiosError(error)) return error.response?.data ?? error.message;
    return error instanceof Error ? error.message : error;
};

/** What a conditional save of an Exchange's field values was answered with. */
export type SetFieldValuesResult =
    | { outcome: 'SAVED'; assignment: SchemaAssignmentDto }
    | { outcome: 'STALE' };

/**
 * Saves only the values it carries, and only while the Exchange's stored values still stand in the
 * version [expectedETag] names.
 *
 * That validator is the one served with the values, or with the previous save, so a save built on
 * values that someone else has since changed is refused instead of silently discarding their change.
 * A read that served no validator has no version to name, which happens only where the resource
 * holds no set of values to be stale about; the save then states that it accepts whichever version
 * is current, which is the truth about what it read and is what keeps such a resource savable.
 *
 * A refusal for a version that has moved on is an answer rather than a failure, because reloading
 * and deciding is the caller's job. Every other refusal is thrown as the server stated it.
 */
export const saveExchangeFieldValues = async (
    exchangeId: string,
    request: SetFieldValuesRequest,
    expectedETag?: string,
): Promise<SetFieldValuesResult> =>
{
    try
    {
        const {data} = await apiClient.patch<SchemaAssignmentDto>(
            `/exchanges/${exchangeId}/fields`,
            request,
            {headers: {[IF_MATCH_HEADER]: expectedETag ?? ANY_VERSION}},
        );
        return {outcome: 'SAVED', assignment: data};
    }
    catch (error: unknown)
    {
        if (namesAVersionThatMovedOn(error)) return {outcome: 'STALE'};
        throw statedRefusal(error);
    }
};
