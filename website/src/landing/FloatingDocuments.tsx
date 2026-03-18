import {useMemo} from "react";
import styles from "./FloatingDocuments.module.css";

type DocCard = {
    id: number;
    top: number;
    width: number;
    height: number;
    duration: number;
    delay: number;
    opacity: number;
    accentColor: string;
    pathClass: string;
    lineWidths: number[];
};

const ACCENT_COLORS = [
    "#5B9BD5",
    "#8E7CC3",
    "#4FB3A8",
    "#5c6da9",
    "#C7A76C",
    "#3f6ba1",
];

const PATH_CLASSES = [styles.pathA, styles.pathB, styles.pathC];

// Deterministic seeded PRNG so cards are stable across re-renders
function makeRand(seed: number): () => number
{
    let s = seed;
    return () => {
        s = (s * 16807) % 2147483647;
        return (s - 1) / 2147483646;
    };
}

export function FloatingDocuments()
{
    const cards = useMemo<DocCard[]>(() => {
        const rand = makeRand(137);

        return Array.from({length: 18}, (_, i) => {
            const lineCount = Math.floor(rand() * 2) + 3;
            return {
                id: i,
                top: rand() * 80 + 4,
                width: Math.floor(rand() * 32) + 64,
                height: Math.floor(rand() * 38) + 78,
                duration: rand() * 18 + 22,
                delay: -(rand() * 35),
                opacity: rand() * 0.18 + 0.2,
                accentColor: ACCENT_COLORS[Math.floor(rand() * ACCENT_COLORS.length)],
                pathClass: PATH_CLASSES[Math.floor(rand() * PATH_CLASSES.length)],
                lineWidths: Array.from({length: lineCount}, () => rand() * 35 + 45),
            };
        });
    }, []);

    return (
        <div className={styles.container} aria-hidden="true">
            {cards.map((card) => (
                <div
                    key={card.id}
                    className={`${styles.card} ${card.pathClass}`}
                    style={{
                        top: `${card.top}%`,
                        width: card.width,
                        height: card.height,
                        opacity: card.opacity,
                        animationDuration: `${card.duration}s`,
                        animationDelay: `${card.delay}s`,
                    }}
                >
                    <div
                        className={styles.accent}
                        style={{backgroundColor: card.accentColor}}
                    />
                    <div className={styles.body}>
                        {card.lineWidths.map((width, idx) => (
                            <div
                                key={idx}
                                className={idx === card.lineWidths.length - 1 ? styles.lineShort : styles.line}
                                style={{width: `${width}%`}}
                            />
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}

