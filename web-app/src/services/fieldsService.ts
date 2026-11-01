import axios from 'axios';
import apiClient from './apiClient';
import {requestFailureOf} from './requestFailure';
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
    SchemaAssignmentStateDto,
    SchemaCompatibility,
    SchemaDefinitionDto,
    SchemaTargetResourceType,
    SchemaVersionDto,
} from '../app/models/models';

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
    targetResourceType?: SchemaTargetResourceType;
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

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        throw requestFailureOf(error);
    }
};

export const listFieldTypes = (): Promise<FieldTypeInfoDto[]> =>
    executeRequest(() => apiClient.get('/fields/types'));

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

export interface ListSchemasParams
{
    scopeKind?: FieldScopeKind;
}

export const listSchemas = (params?: ListSchemasParams): Promise<SchemaDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/schemas/definitions', {params}));

export const getSchema = (id: string): Promise<SchemaDefinitionDto> =>
    executeRequest(() => apiClient.get(`/schemas/definitions/${id}`));

export const getSchemaVersion = (id: string): Promise<SchemaVersionDto> =>
    executeRequest(() => apiClient.get(`/schemas/versions/${id}`));

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

export const getExchangeSchema = (exchangeId: string): Promise<SchemaAssignmentStateDto> =>
    executeRequest(() => apiClient.get(`/exchanges/${exchangeId}/schema`));

const IF_MATCH_HEADER = 'If-Match';

const IF_NONE_MATCH_HEADER = 'If-None-Match';

const ANY_VERSION = '*';

export const assignExchangeSchema = (
    exchangeId: string,
    request: AssignSchemaRequest,
): Promise<SchemaAssignmentDto> =>
    executeRequest(() => apiClient.put(
        `/exchanges/${exchangeId}/schema`,
        request,
        {headers: {[IF_NONE_MATCH_HEADER]: ANY_VERSION}},
    ));

export const unassignExchangeSchema = (exchangeId: string, expectedETag: string): Promise<void> =>
    executeRequest(() => apiClient.delete(
        `/exchanges/${exchangeId}/schema`,
        {headers: {[IF_MATCH_HEADER]: expectedETag}},
    ));

const STALE_VERSION_CODE = 'FIELDS_PRECONDITION_STALE';

const namesAVersionThatMovedOn = (error: unknown): boolean =>
    axios.isAxiosError(error)
    && error.response?.status === 412
    && (error.response.data as ResponseError | undefined)?.reasonCode === STALE_VERSION_CODE;

const statedRefusal = (error: unknown): unknown =>
{
    if (axios.isAxiosError(error)) return error.response?.data ?? error.message;
    return error instanceof Error ? error.message : error;
};

export type SetFieldValuesResult =
    | { outcome: 'SAVED'; assignment: SchemaAssignmentDto }
    | { outcome: 'STALE' };

export const saveExchangeFieldValues = async (
    exchangeId: string,
    request: SetFieldValuesRequest,
    expectedETag: string,
): Promise<SetFieldValuesResult> =>
{
    try
    {
        const {data} = await apiClient.patch<SchemaAssignmentDto>(
            `/exchanges/${exchangeId}/fields`,
            request,
            {headers: {[IF_MATCH_HEADER]: expectedETag}},
        );
        return {outcome: 'SAVED', assignment: data};
    }
    catch (error: unknown)
    {
        if (namesAVersionThatMovedOn(error)) return {outcome: 'STALE'};
        throw statedRefusal(error);
    }
};
