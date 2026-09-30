import {useEffect, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {getInformationRequestCapabilities} from "../../../services/informationRequestCapabilityService.ts";
import {CurrentSessionDto, InformationRequestCapabilitiesDto} from "../../models/models.tsx";

interface ScopedCapabilities
{
    scope: string;
    capabilities: InformationRequestCapabilitiesDto | null;
}

const pendingReads = new Map<string, Promise<InformationRequestCapabilitiesDto>>();

const readFor = (scope: string): Promise<InformationRequestCapabilitiesDto> =>
{
    const pending = pendingReads.get(scope);
    if (pending) return pending;
    const read = getInformationRequestCapabilities().finally(() => pendingReads.delete(scope));
    pendingReads.set(scope, read);
    return read;
};

const scopeOf = (session: CurrentSessionDto | null): string | null =>
    session === null
        ? null
        : [
            session.userId,
            session.activeOrganizationId ?? "",
            session.subscription?.planCode ?? "",
            session.subscription?.status ?? "",
            session.subscription?.enforcementMode ?? "",
        ].join("|");

export const useInformationRequestCapabilities = (): InformationRequestCapabilitiesDto | null =>
{
    const {currentSession} = useAuth();
    const scope = scopeOf(currentSession);
    const [loaded, setLoaded] = useState<ScopedCapabilities | null>(null);

    useEffect(() =>
    {
        if (scope === null) return;
        let current = true;
        readFor(scope)
            .then(capabilities =>
            {
                if (current) setLoaded({scope, capabilities});
            })
            .catch(() =>
            {
                if (current) setLoaded({scope, capabilities: null});
            });
        return () =>
        {
            current = false;
        };
    }, [scope]);

    return loaded !== null && loaded.scope === scope ? loaded.capabilities : null;
};
