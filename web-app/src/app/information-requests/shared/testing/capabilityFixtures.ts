import {
    InformationRequestCapabilitiesDto,
    InformationRequestStandingReason,
    PlanCode,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../../../models/models.tsx";

export const informationRequestCapabilities = (
    overrides: Partial<InformationRequestCapabilitiesDto> = {},
): InformationRequestCapabilitiesDto => ({
    ownerType: SubscriptionOwnerType.USER,
    planCode: PlanCode.PERSONAL,
    subscriptionStatus: SubscriptionStatus.ACTIVE,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
    featureIncluded: true,
    newWorkAvailable: true,
    operationallySuspended: false,
    typedAnswersAvailable: true,
    personalTemplatesAvailable: true,
    assignedWork: false,
    holdsRequests: false,
    quotas: {openRequests: 0, committedEvidenceBytes: 0},
    ...overrides,
});

export const capabilitiesWithoutTheFeature = (
    overrides: Partial<InformationRequestCapabilitiesDto> = {},
): InformationRequestCapabilitiesDto => informationRequestCapabilities({
    planCode: PlanCode.FREE,
    featureIncluded: false,
    newWorkAvailable: false,
    newWorkUnavailableReason: InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
    personalTemplatesAvailable: false,
    ...overrides,
});
