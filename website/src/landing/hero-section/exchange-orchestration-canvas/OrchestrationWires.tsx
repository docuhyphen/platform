import {useExchangeOrchestrationCanvasStyles} from "./ExchangeOrchestrationCanvasStyles.tsx";

interface WirePath
{
    id: string;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
}

const wirePaths: readonly WirePath[] = [
    {id: "participants", x1: 16, y1: 10, x2: 35, y2: 34},
    {id: "documents", x1: 84, y1: 10, x2: 65, y2: 34},
    {id: "workflows", x1: 92, y1: 46, x2: 70, y2: 50},
    {id: "notifications", x1: 84, y1: 90, x2: 65, y2: 66},
    {id: "audit", x1: 16, y1: 90, x2: 35, y2: 66},
    {id: "integrations", x1: 8, y1: 46, x2: 30, y2: 50},
];

export function OrchestrationWires()
{
    const styles = useExchangeOrchestrationCanvasStyles();

    return (
        <svg
            id="orchestration-wires"
            className={styles.wireLayer}
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            aria-hidden="true"
            focusable="false"
        >
            {wirePaths.map((wire) => (
                <line
                    id={`orchestration-wire-${wire.id}`}
                    key={wire.id}
                    className={styles.wire}
                    x1={wire.x1}
                    y1={wire.y1}
                    x2={wire.x2}
                    y2={wire.y2}
                    vectorEffect="non-scaling-stroke"
                />
            ))}
        </svg>
    );
}
