import {useEffect, useState} from "react";
import {CommunicationScope, CommunicationSummaryDto} from "../../models/models.tsx";
import {listCommunications} from "../../../services/communicationService.ts";

const matches = (communication: CommunicationSummaryDto, search: string): boolean => {
    const term = search.toLowerCase();
    return [communication.name, communication.subject, communication.summary ?? ""]
        .some(text => text.toLowerCase().includes(term));
};

export const useCommunicationChoices = (open: boolean, scopes: CommunicationScope[]) => {
    const firstScope = scopes[0];
    const [activeTab, setActiveTab] = useState<CommunicationScope>(firstScope);
    const [communications, setCommunications] = useState<CommunicationSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState("");
    const [pending, setPending] = useState<CommunicationSummaryDto | null>(null);

    useEffect(() => {
        if (!open) return;
        setActiveTab(firstScope);
        setSearch("");
        setPending(null);
    }, [firstScope, open]);

    useEffect(() => {
        if (!open) return;
        let current = true;
        setLoading(true);
        listCommunications({scope: activeTab})
            .then(list => {
                if (current) setCommunications(list.filter(communication => communication.isActive));
            })
            .catch(() => {
                if (current) setCommunications([]);
            })
            .finally(() => {
                if (current) setLoading(false);
            });
        return () => {
            current = false;
        };
    }, [activeTab, open]);

    const chooseScope = (value: unknown) => {
        const scope = scopes.find(candidate => candidate === value);
        if (!scope) return;
        setActiveTab(scope);
        setCommunications([]);
        setPending(null);
    };

    return {
        activeTab,
        chooseScope,
        loading,
        search,
        setSearch,
        pending,
        setPending,
        filtered: communications.filter(communication => matches(communication, search)),
    };
};
