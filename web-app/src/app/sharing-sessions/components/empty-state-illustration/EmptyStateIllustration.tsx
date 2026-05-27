import React from 'react';

interface Props
{
    className?: string;
}

const EmptyStateIllustration: React.FC<Props> = ({className}) =>
{
    return (
        <svg
            className={className}
            viewBox="0 0 400 280"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            role="img"
            aria-label="No sharing sessions yet"
        >
            <rect
                x="60"
                y="40"
                width="150"
                height="200"
                rx="8"
                stroke="currentColor"
                strokeWidth="2.5"
                fill="none"
                opacity="0.9"
            />
            <line x1="85" y1="80"  x2="185" y2="80"  stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.55"/>
            <line x1="85" y1="105" x2="185" y2="105" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4"/>
            <line x1="85" y1="130" x2="160" y2="130" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4"/>
            <line x1="85" y1="155" x2="175" y2="155" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4"/>
            <line x1="85" y1="180" x2="140" y2="180" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4"/>

            <path
                d="M 215 130 Q 270 90 340 105"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeDasharray="2 7"
                fill="none"
                opacity="0.5"
            />

            <g transform="translate(295 60) rotate(20)">
                <path
                    d="M 0 30 L 70 0 L 50 60 L 38 38 Z"
                    stroke="currentColor"
                    strokeWidth="2.5"
                    strokeLinejoin="round"
                    fill="none"
                />
                <path
                    d="M 70 0 L 38 38"
                    stroke="currentColor"
                    strokeWidth="2.5"
                    strokeLinejoin="round"
                    fill="none"
                    opacity="0.6"
                />
            </g>
        </svg>
    );
};

export default EmptyStateIllustration;
