import React, {useEffect, useRef, useState} from 'react';
import {mergeClasses, Text} from "@fluentui/react-components";
import {useCarouselStyles} from './CarouselStyles';

interface Slide
{
    title: string;
    description: string;
}

interface CarouselProps
{
    slides: Slide[];
}

const SignInSignUpTipsCarousel: React.FC<CarouselProps> = ({slides}) =>
{
    const [currentIndex, setCurrentIndex] = useState(0);
    const intervalRef = useRef<NodeJS.Timeout | null>(null);
    const styles = useCarouselStyles();

    const startAutoPlay = () =>
    {
        intervalRef.current = setInterval(() =>
        {
            setCurrentIndex(prevIndex => (prevIndex + 1) % slides.length);
        }, 3000);
    };

    const stopAutoPlay = () =>
    {
        if (intervalRef.current)
        {
            clearInterval(intervalRef.current);
        }
    };

    useEffect(() =>
    {
        startAutoPlay();
        return () => stopAutoPlay();
    }, []);

    return (
        <div
            id={"authorization-tips-carousel"}
            className={styles.carousel}
            onMouseEnter={stopAutoPlay}
            onMouseLeave={startAutoPlay}>
            <div
                id={"authorization-tips-carousel-slides"}
                className={styles.carouselInner}>
                {slides.map((slide, index) => (
                    <div
                        id={`authorization-tip-slide-${index}`}
                        className={mergeClasses(
                            styles.carouselItem,
                            currentIndex === index && styles.carouselItemActive,
                        )}
                        key={index}
                    >
                        <Text
                            id={`authorization-tip-title-${index}`}
                            size={500}>
                            {slide.title}
                        </Text>
                        <p id={`authorization-tip-description-${index}`}>{slide.description}</p>
                    </div>
                ))}
            </div>
            <div
                id={"authorization-tips-carousel-dots"}
                className={styles.carouselDots}>
                {slides.map((_, index) => (
                    <span
                        id={`authorization-tip-dot-${index}`}
                        key={index}
                        className={mergeClasses(
                            styles.dot,
                            currentIndex === index && styles.dotActive,
                        )}
                        onClick={() => setCurrentIndex(index)}>
                    </span>
                ))}
            </div>
        </div>
    );
};

export default SignInSignUpTipsCarousel;
