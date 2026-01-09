import React, {useEffect, useRef, useState} from 'react';
import {Text} from "@fluentui/react-components";
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
            className={styles.carousel}
            onMouseEnter={stopAutoPlay}
            onMouseLeave={startAutoPlay}
        >
            <div className={styles.carouselInner} style={{transform: `translateX(-${currentIndex * 100}%)`}}>
                {slides.map((slide, index) => (
                    <div className={styles.carouselItem} key={index}>
                        <Text size={500}>{slide.title}</Text>
                        <p>{slide.description}</p>
                    </div>
                ))}
            </div>
            <div className={styles.carouselDots}>
                {slides.map((_, index) => (
                    <span
                        key={index}
                        className={`${styles.dot} ${currentIndex === index ? styles.dotActive : ''}`}
                        onClick={() => setCurrentIndex(index)}
                    ></span>
                ))}
            </div>
        </div>
    );
};

export default SignInSignUpTipsCarousel;