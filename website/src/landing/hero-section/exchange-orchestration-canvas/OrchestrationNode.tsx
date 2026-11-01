import {Text} from "@fluentui/react-components";
import type {ReactNode} from "react";

interface OrchestrationNodeProps
{
    id: string;
    label: string;
    className: string;
    icon: ReactNode;
}

export function OrchestrationNode({id, label, className, icon}: OrchestrationNodeProps)
{
    return (
        <div
            id={id}
            className={className}
        >
            {icon}
            <Text id={"orchestration-node-text-1"}
                  weight="semibold">{label}</Text>
        </div>
    );
}
