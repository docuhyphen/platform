import {useCallback, useEffect, useState} from "react";
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
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";

export const useClockPolicies = () =>
{
    const [policies, setPolicies] = useState<InformationRequestClockPolicyDto[] | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(async () =>
    {
        try
        {
            setPolicies(await getInformationRequestClockPolicies());
            setLoadError(null);
        }
        catch (caught: unknown)
        {
            setLoadError(informationRequestRefusalMessage(caught, "The due date policies could not be loaded."));
        }
    }, []);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const save = async (work: () => Promise<InformationRequestClockPolicyDto>, done: string): Promise<string | null> =>
    {
        setBusy(true);
        setNotice(null);
        try
        {
            await work();
            setNotice(done);
            await load();
            return null;
        }
        catch (caught: unknown)
        {
            return informationRequestRefusalMessage(caught, "The due date policy could not be saved.");
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        policies,
        loadError,
        notice,
        busy,
        define: (request: DefineInformationRequestClockPolicyRequest) =>
            save(() => defineInformationRequestClockPolicy(request), "The due date policy was created."),
        publish: (policy: InformationRequestClockPolicyDto, definition: InformationRequestClockPolicyDefinitionRequest) =>
            save(() => publishInformationRequestClockPolicyVersion(policy.id, definition), `A new version of ${policy.displayName} was published.`),
    };
};
