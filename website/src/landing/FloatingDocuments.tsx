import type React from "react";
import styles from "./FloatingDocuments.module.css";

type CardDef = {
    id: number;
    top: number;
    left: number;
    width: number;
    height: number;
    duration: number;
    delay: number;
    opacity: number;
    accentColor: string;
    lines: number[];
    arcUp: boolean;
};

const CARDS: CardDef[] = [
    { id: 0, top: 17, left: 9,  width: 82,  height: 100, duration: 25, delay: 0,   opacity: 0.28, accentColor: "#5B9BD5", lines: [80, 65, 50], arcUp: true  },
    { id: 1, top: 36, left: 11, width: 70,  height: 88,  duration: 28, delay: 3,   opacity: 0.24, accentColor: "#4FB3A8", lines: [75, 55],     arcUp: false },
    { id: 2, top: 56, left: 8,  width: 86,  height: 106, duration: 22, delay: 6,   opacity: 0.28, accentColor: "#8E7CC3", lines: [85, 70, 55], arcUp: true  },
    { id: 3, top: 72, left: 13, width: 74,  height: 92,  duration: 29, delay: 9,   opacity: 0.22, accentColor: "#C7A76C", lines: [70, 60],     arcUp: false },
    { id: 4, top: 27, left: 10, width: 78,  height: 96,  duration: 24, delay: 13,  opacity: 0.26, accentColor: "#5c6da9", lines: [90, 75, 60], arcUp: false },
    { id: 5, top: 48, left: 12, width: 80,  height: 98,  duration: 27, delay: 16,  opacity: 0.25, accentColor: "#5B9BD5", lines: [80, 65],     arcUp: true  },
    { id: 6, top: 64, left: 9,  width: 68,  height: 86,  duration: 25, delay: 20,  opacity: 0.23, accentColor: "#4FB3A8", lines: [85, 70, 55], arcUp: false },
];

export function FloatingDocuments()
{
    return (
        <div className={styles.container} aria-hidden="true">

            {/* Sender hub - left side */}
            <div className={`${styles.hub} ${styles.hubLeft}`}>
                <svg viewBox="0 0 72 92" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="1" y="1" width="70" height="90" rx="7"
                          fill="white" fillOpacity="0.75"
                          stroke="rgba(78,109,150,0.45)" strokeWidth="1.5"/>
                    <rect x="12" y="13" width="48" height="5" rx="2.5" fill="rgba(78,109,150,0.40)"/>
                    <rect x="12" y="24" width="38" height="4" rx="2"  fill="rgba(78,109,150,0.25)"/>
                    <rect x="12" y="33" width="44" height="4" rx="2"  fill="rgba(78,109,150,0.25)"/>
                    <rect x="12" y="42" width="28" height="4" rx="2"  fill="rgba(78,109,150,0.18)"/>
                    {/* Upload arrow */}
                    <line x1="36" y1="72" x2="36" y2="60"
                          stroke="rgba(91,155,213,0.8)" strokeWidth="2" strokeLinecap="round"/>
                    <polyline points="28,67 36,59 44,67"
                              stroke="rgba(91,155,213,0.8)" strokeWidth="2"
                              strokeLinecap="round" strokeLinejoin="round" fill="none"/>
                </svg>
                <span className={styles.hubLabel}>Sender</span>
            </div>

            {/* Central shield / secure checkpoint */}
            <div className={styles.shieldWrap}>
                <svg viewBox="0 0 56 64" fill="none" xmlns="http://www.w3.org/2000/svg"
                     className={styles.shield}>
                    <path d="M28 4 L52 14 L52 34 C52 49 40 59 28 63 C16 59 4 49 4 34 L4 14 Z"
                          fill="rgba(91,155,213,0.10)" stroke="rgba(91,155,213,0.50)" strokeWidth="1.5"/>
                    {/* Lock body */}
                    <rect x="17" y="30" width="22" height="17" rx="3.5"
                          fill="rgba(91,155,213,0.40)"/>
                    {/* Lock shackle */}
                    <path d="M21 30 L21 25 C21 20.5 35 20.5 35 25 L35 30"
                          stroke="rgba(91,155,213,0.55)" strokeWidth="2"
                          strokeLinecap="round" fill="none"/>
                    {/* Keyhole */}
                    <circle cx="28" cy="37.5" r="2.5" fill="rgba(255,255,255,0.65)"/>
                    <rect x="26.5" y="39" width="3" height="4" rx="1"
                          fill="rgba(255,255,255,0.65)"/>
                </svg>
            </div>

            {/* Recipient hub - right side */}
            <div className={`${styles.hub} ${styles.hubRight}`}>
                <svg viewBox="0 0 72 92" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="1" y="1" width="70" height="90" rx="7"
                          fill="white" fillOpacity="0.75"
                          stroke="rgba(78,109,150,0.45)" strokeWidth="1.5"/>
                    <rect x="12" y="13" width="48" height="5" rx="2.5" fill="rgba(78,109,150,0.40)"/>
                    <rect x="12" y="24" width="38" height="4" rx="2"  fill="rgba(78,109,150,0.25)"/>
                    <rect x="12" y="33" width="44" height="4" rx="2"  fill="rgba(78,109,150,0.25)"/>
                    <rect x="12" y="42" width="28" height="4" rx="2"  fill="rgba(78,109,150,0.18)"/>
                    {/* Download arrow */}
                    <line x1="36" y1="59" x2="36" y2="71"
                          stroke="rgba(79,179,168,0.8)" strokeWidth="2" strokeLinecap="round"/>
                    <polyline points="28,64 36,72 44,64"
                              stroke="rgba(79,179,168,0.8)" strokeWidth="2"
                              strokeLinecap="round" strokeLinejoin="round" fill="none"/>
                </svg>
                <span className={styles.hubLabel}>Recipient</span>
            </div>

            {/* Animated document cards */}
            {CARDS.map((card) => (
                <div
                    key={card.id}
                    className={`${styles.card} ${card.arcUp ? styles.arcUp : styles.arcDown}`}
                    style={{
                        top: `${card.top}%`,
                        left: `${card.left}vw`,
                        width: card.width,
                        height: card.height,
                        animationDuration: `${card.duration}s`,
                        animationDelay: `${card.delay}s`,
                        "--card-opacity": card.opacity,
                    } as React.CSSProperties}
                >
                    <div className={styles.accent} style={{backgroundColor: card.accentColor}}/>
                    <div className={styles.body}>
                        {card.lines.map((width, idx) => (
                            <div
                                key={idx}
                                className={idx === card.lines.length - 1 ? styles.lineShort : styles.line}
                                style={{width: `${width}%`}}
                            />
                        ))}
                    </div>
                </div>
            ))}

        </div>
    );
}
