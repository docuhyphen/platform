import {Button, Card, Text, mergeClasses} from "@fluentui/react-components";
import {ChevronLeft20Regular, ChevronRight20Regular} from "@fluentui/react-icons";
import {useState} from "react";
import {industrySlides} from "./industryCarouselSlides.ts";
import {useIndustryCarouselSectionStyles} from "./IndustryCarouselSectionStyles.tsx";

export function IndustryCarouselSection()
{
    const styles = useIndustryCarouselSectionStyles();
    const [activeIndustrySlide, setActiveIndustrySlide] = useState<number>(0);

    const updateActiveSlide = (nextIndex: number) => {
        setActiveIndustrySlide(nextIndex);
        window.dispatchEvent(new CustomEvent("industry-slide-change", {
            detail: {index: nextIndex},
        }));
    };

    const goToPreviousIndustrySlide = () => {
        const nextIndex = activeIndustrySlide === 0 ? industrySlides.length - 1 : activeIndustrySlide - 1;
        updateActiveSlide(nextIndex);
    };

    const goToNextIndustrySlide = () => {
        updateActiveSlide((activeIndustrySlide + 1) % industrySlides.length);
    };

    return (
        <section id={"appImageSection"}
                 className={styles.section}>
            <div id={"industry-carousel-viewport"}
                 className={styles.viewport}>
                <div id={"industry-carousel-track"}
                     className={styles.track}
                     style={{transform: `translateX(-${activeIndustrySlide * 100}%)`}}>
                    {industrySlides.map((slide, index) => (
                        <article id={`industry-carousel-slide-${index + 1}`}
                                 key={slide.title}
                                 className={styles.slide}>
                            <div id={`industry-carousel-slide-visual-${index + 1}`}
                                 className={mergeClasses(styles.imagePlaceholder, styles[slide.visualClass])}
                                 aria-label={`${slide.title} screenshot placeholder`}>
                                <div id={`industry-carousel-slide-frame-${index + 1}`}
                                     className={styles.imageFrame}>
                                    <img id={`industry-carousel-slide-image-${index + 1}`}
                                         className={styles.image}
                                         src={slide.screenshotSrc}
                                         alt={`${slide.title} app screenshot`}
                                         loading="lazy"/>
                                    <Card id={`industry-carousel-slide-card-${index + 1}`}
                                          className={mergeClasses(styles.titleCard, styles.imageOverlay, styles[slide.visualClass])}>
                                        <Text id={`industry-carousel-slide-title-${index + 1}`}
                                              weight="semibold"
                                              className={styles.titleText}>{slide.title}</Text>
                                    </Card>
                                </div>
                            </div>
                        </article>
                    ))}
                </div>
            </div>

            <div id={"industry-carousel-controls"}
                 className={styles.controls}>
                <Button id={"industry-carousel-previous"}
                        appearance="secondary"
                        icon={<ChevronLeft20Regular id={"industry-carousel-previous-icon"}/>}
                        shape="circular"
                        onClick={goToPreviousIndustrySlide}
                        aria-label="Show previous industry screenshot"/>
                <Text id={"industry-carousel-position"}>
                    {activeIndustrySlide + 1}/{industrySlides.length}
                </Text>
                <Button id={"industry-carousel-next"}
                        appearance="secondary"
                        icon={<ChevronRight20Regular id={"industry-carousel-next-icon"}/>}
                        shape="circular"
                        onClick={goToNextIndustrySlide}
                        aria-label="Show next industry screenshot"/>
            </div>

            <div id={"industry-carousel-indicators"}
                 className={styles.indicators}>
                {industrySlides.map((slide, index) => (
                    <Button id={`industry-carousel-indicator-${index + 1}`}
                            key={slide.title}
                            appearance="transparent"
                            shape="circular"
                            className={mergeClasses(
                                styles.indicator,
                                index === activeIndustrySlide && styles.indicatorActive,
                            )}
                            onClick={() => updateActiveSlide(index)}
                            aria-label={`Go to ${slide.title}`}
                            aria-current={index === activeIndustrySlide ? "true" : undefined}/>
                ))}
            </div>
        </section>
    );
}
