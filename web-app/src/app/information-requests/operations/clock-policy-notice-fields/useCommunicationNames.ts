import {useCallback, useEffect, useRef, useState} from "react";
import {getCommunication} from "../../../../services/communicationService.ts";
import {CommunicationSummaryDto} from "../../../models/models.tsx";

export const useCommunicationNames = (ids: (string | undefined)[]) => {
    const [names, setNames] = useState<Record<string, string>>({});
    const requested = useRef(new Set<string>());
    const wanted = ids.filter((id): id is string => Boolean(id)).join(",");

    useEffect(() => {
        wanted.split(",")
            .filter(id => id && !requested.current.has(id))
            .forEach(id => {
                requested.current.add(id);
                getCommunication(id)
                    .then(communication => setNames(known => ({...known, [id]: communication.name})))
                    .catch(() => requested.current.delete(id));
            });
    }, [wanted]);

    const remember = useCallback((communication: CommunicationSummaryDto) => {
        requested.current.add(communication.id);
        setNames(known => ({...known, [communication.id]: communication.name}));
    }, []);

    return {names, remember};
};
