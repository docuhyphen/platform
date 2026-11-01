import {
    AvailableVariablesDto,
    CommunicationDto,
    CommunicationScope,
    CommunicationSummaryDto,
    CreateCommunicationRequest,
    UpdateCommunicationRequest,
} from '../../models/models';
import {CommunicationEditorValues} from './communicationEditorTypes';

export const emptyCommunicationValues: CommunicationEditorValues = {
    name: '',
    summary: '',
    description: '',
    subject: '',
    body: '',
    tags: [],
};

export const communicationValuesFrom = (
    communication: CommunicationSummaryDto | CommunicationDto | undefined,
): CommunicationEditorValues =>
{
    if (!communication) return emptyCommunicationValues;
    return {
        name: communication.name,
        summary: communication.summary ?? '',
        description: (communication as CommunicationDto).description ?? '',
        subject: communication.subject,
        body: (communication as CommunicationDto).body ?? '',
        tags: communication.generalTags ?? [],
    };
};

export const validateCommunicationValues = (values: CommunicationEditorValues): string | null =>
{
    if (!values.name.trim()) return 'Name is required';
    if (!values.subject.trim()) return 'Subject is required';
    if (!values.body.trim()) return 'Body is required';
    return null;
};

const trimmedCommunicationFields = (values: CommunicationEditorValues) => ({
    name: values.name.trim(),
    summary: values.summary.trim() || undefined,
    description: values.description.trim() || undefined,
    subject: values.subject.trim(),
    body: values.body.trim(),
    generalTags: values.tags,
});

export const buildUpdateCommunicationRequest = (values: CommunicationEditorValues): UpdateCommunicationRequest =>
    trimmedCommunicationFields(values);

export const buildCreateCommunicationRequest = (
    values: CommunicationEditorValues,
    scope: CommunicationScope,
    isTemplate: boolean,
): CreateCommunicationRequest => ({
    ...trimmedCommunicationFields(values),
    scope,
    isActive: true,
    isTemplate,
});

export const communicationVariableHints = (availableVariables: AvailableVariablesDto | null): string[] =>
    availableVariables
        ? [
            ...availableVariables.system.map(v => `{{${v.token}}}`),
            ...availableVariables.org.map(v => `{{${v.key}}}`),
            ...availableVariables.personal.map(v => `{{${v.key}}}`),
            ...availableVariables.sequences.map(v => `{{SEQ:${v.key}}}`),
        ]
        : [];
