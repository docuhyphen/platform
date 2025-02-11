import React, {useEffect, useRef, useState} from 'react';
import './Carousel.css';
import {Text} from "@fluentui/react-components";

const SignUpCarousel: React.FC = () => {
    const [currentIndex, setCurrentIndex] = useState(0);
    const slides = [
        {
            title: "Secure & Compliant Document Sharing",
            description: "Exchange sensitive documents with end-to-end encryption and compliance features, ensuring your business meets data protection laws."
        },
        {
            title: "Verified Business & Secure Access",
            description: "Every business is verified to ensure legitimacy, preventing fraud and unauthorized document exchanges. Share with confidence, knowing your data is protected."
        },
        {
            title: "Digital Signatures & Watermarking",
            description: "Every document is protected with unique digital signatures and invisible watermarking, ensuring authenticity and traceability."
        },
        {
            title: "Audit Trails & Access Logs",
            description: " Get full visibility with detailed audit trails. Track who accessed, shared, or modified documents in real time."
        }
    ];

    const intervalRef = useRef<NodeJS.Timeout | null>(null);

    const startAutoPlay = () => {
        intervalRef.current = setInterval(() => {
            setCurrentIndex(prevIndex => (prevIndex + 1) % slides.length);
        }, 3000);
    };

    const stopAutoPlay = () => {
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
        }
    };

    useEffect(() => {
        startAutoPlay();
        return () => stopAutoPlay();
    }, []);

    return (
        <div
            className="carousel"
            onMouseEnter={stopAutoPlay}
            onMouseLeave={startAutoPlay}
        >
            <div className="carousel-inner" style={{ transform: `translateX(-${currentIndex * 100}%)` }}>
                {slides.map((slide, index) => (
                    <div className="carousel-item" key={index}>
                        <Text size={500}>{slide.title}</Text>
                        <p>{slide.description}</p>
                    </div>
                ))}
            </div>
            <div className="carousel-dots">
                {slides.map((_, index) => (
                    <span
                        key={index}
                        className={`dot ${currentIndex === index ? 'active' : ''}`}
                        onClick={() => setCurrentIndex(index)}
                    ></span>
                ))}
            </div>
        </div>
    );
};

export default SignUpCarousel;