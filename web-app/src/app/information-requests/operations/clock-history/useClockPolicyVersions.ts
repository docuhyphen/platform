import {useEffect, useState} from "react";
import {getInformationRequestClockPolicies} from "../../../../services/informationRequestAdministrationService.ts";
import {ClockPolicyVersionEntry} from "./clockHistoryText.ts";

export const useClockPolicyVersions = (): Map<string, ClockPolicyVersionEntry> => {
    const [versions, setVersions] = useState<Map<string, ClockPolicyVersionEntry>>(new Map());

    useEffect(() => {
        let current = true;
        getInformationRequestClockPolicies()
            .then(policies => {
                if (!current) return;
                setVersions(new Map(policies.flatMap(policy =>
                    policy.versions.map(version => [version.id, {
                        policy,
                        version
                    }] as [string, ClockPolicyVersionEntry]))));
            })
            .catch((): undefined => undefined);
        return () => {
            current = false;
        };
    }, []);

    return versions;
};
