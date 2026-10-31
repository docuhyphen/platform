import {useEffect, useState} from "react";
import {getInformationRequestHeader} from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestExecutionStandingDto} from "../../../models/models.tsx";
import {Scoped, valueInScope} from "../../shared/useScopeGuard.ts";

export const useOperationsRequestStanding = (requestId: string): InformationRequestExecutionStandingDto | null => {
    const [standing, setStanding] = useState<Scoped<InformationRequestExecutionStandingDto | null> | null>(null);

    useEffect(() => {
        let active = true;
        getInformationRequestHeader(requestId)
            .then(header => {
                if (active) setStanding({scope: requestId, value: header.executionStanding});
            })
            .catch(() => {
                if (active) setStanding({scope: requestId, value: null});
            });
        return () => {
            active = false;
        };
    }, [requestId]);

    return valueInScope(standing, requestId);
};
