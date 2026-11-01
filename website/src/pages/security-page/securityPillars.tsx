import {
    ClipboardTaskListLtr24Regular,
    Globe24Regular,
    LockClosed24Regular,
    Person24Regular,
    Server24Regular,
    ShieldCheckmark24Regular,
} from "@fluentui/react-icons";
import type {ReactNode} from "react";

export type SecurityPillar = {
    icon: ReactNode;
    title: string;
    body: string;
};

export const securityPillars: SecurityPillar[] = [
    {
        icon: <LockClosed24Regular id={"security-page-lock-closed24-regular-1"}
                                   aria-hidden="true"/>,
        title: "Protected documents",
        body: "Connections and stored documents are protected with encryption controls throughout the Exchange lifecycle.",
    },
    {
        icon: <Server24Regular id={"security-page-server24-regular-1"}
                               aria-hidden="true"/>,
        title: "Controlled infrastructure",
        body: "The platform uses managed cloud infrastructure, protected storage, backups, and monitored application services.",
    },
    {
        icon: <Person24Regular id={"security-page-person24-regular-1"}
                               aria-hidden="true"/>,
        title: "Identity and access",
        body: "Organization boundaries, roles, and session controls restrict who can reach an Exchange and its documents.",
    },
    {
        icon: <ClipboardTaskListLtr24Regular id={"security-page-clipboard-task-list-ltr24-regular-1"}
                                             aria-hidden="true"/>,
        title: "Traceable activity",
        body: "Relevant document and administration activity is recorded so teams can review what happened and when.",
    },
    {
        icon: <ShieldCheckmark24Regular id={"security-page-shield-checkmark24-regular-1"}
                                        aria-hidden="true"/>,
        title: "Workflow controls",
        body: "Configurable review and approval steps help teams apply consistent controls to sensitive document work.",
    },
    {
        icon: <Globe24Regular id={"security-page-globe24-regular-1"}
                              aria-hidden="true"/>,
        title: "Tenant isolation",
        body: "Authentication and authorization checks keep organization data scoped to the appropriate principals.",
    },
];
