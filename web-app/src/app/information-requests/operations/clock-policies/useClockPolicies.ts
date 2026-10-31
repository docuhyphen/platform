import {useState} from "react";
import {
    defineInformationRequestClockPolicy,
    getInformationRequestClockPolicies,
    publishInformationRequestClockPolicyVersion,
} from "../../../../services/informationRequestAdministrationService.ts";
import {
    DefineInformationRequestClockPolicyRequest,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyDto,
} from "../../../models/models.tsx";
import {
    informationRequestRefusalCode,
    informationRequestRefusalMessage
} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";

const CLOCK_POLICIES = "clock-policies";

export interface ClockPolicyRefusal {
    message: string;
    code?: string;
}

const loadRefusal = (refusal: unknown) =>
    informationRequestRefusalMessage(refusal, "The due date policies could not be loaded.");

export const useClockPolicies = () =>
{
    const policies = useServerRead(CLOCK_POLICIES, getInformationRequestClockPolicies, loadRefusal);
    const [notice, setNotice] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const keyed = useStableIdempotencyKey();

    const save = async (work: () => Promise<InformationRequestClockPolicyDto>, done: string): Promise<ClockPolicyRefusal | null> =>
    {
        setBusy(true);
        setNotice(null);
        try
        {
            await work();
            setNotice(done);
            await policies.reload();
            return null;
        }
        catch (caught: unknown)
        {
            return {
                message: informationRequestRefusalMessage(caught, "The due date policy could not be saved."),
                code: informationRequestRefusalCode(caught),
            };
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        policies: policies.value,
        loadError: policies.error,
        reload: policies.reload,
        notice,
        busy,
        define: (request: DefineInformationRequestClockPolicyRequest) =>
            save(
                () => keyed("define-clock-policy", request, key => defineInformationRequestClockPolicy(request, key)),
                "The due date policy was created.",
            ),
        publish: (policy: InformationRequestClockPolicyDto, definition: InformationRequestClockPolicyDefinitionRequest) =>
            save(
                () => keyed(`publish-clock-policy-${policy.id}`, definition, key => publishInformationRequestClockPolicyVersion(policy.id, definition, key)),
                `A new version of ${policy.displayName} was published.`,
            ),
    };
};
