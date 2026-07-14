import type {IndustryExperience} from "../featureContent.ts";
import {IndustryDemoHeader} from "./IndustryDemoHeader.tsx";
import {IndustryDocumentView} from "./IndustryDocumentView.tsx";
import {IndustryExchangeList} from "./IndustryExchangeList.tsx";
import {
    INDUSTRY_DEMO_CANVAS_WIDTH_REM,
    INDUSTRY_DEMO_DEFAULT_SCALE,
    useIndustryProductDemoStyles,
} from "./IndustryProductDemoStyles.tsx";
import {useEffect, useRef, useState, type CSSProperties} from "react";

interface IndustryProductDemoProps
{
    industry: IndustryExperience;
}

interface IndustryDemoInlineVars extends CSSProperties
{
    "--industry-demo-scale"?: string;
}

export function IndustryProductDemo({industry}: IndustryProductDemoProps)
{
    const styles = useIndustryProductDemoStyles();
    const shellRef = useRef<HTMLDivElement | null>(null);
    const [scale, setScale] = useState(INDUSTRY_DEMO_DEFAULT_SCALE);

    useEffect(() =>
    {
        const shellElement = shellRef.current;

        if (!shellElement)
        {
            return;
        }

        const canvasWidth = INDUSTRY_DEMO_CANVAS_WIDTH_REM * 16;
        const updateScale = () =>
        {
            const availableWidth = shellElement.clientWidth;
            const nextScale = availableWidth / canvasWidth;

            setScale(nextScale);
        };

        updateScale();

        const resizeObserver = new ResizeObserver(updateScale);
        resizeObserver.observe(shellElement);

        return () => resizeObserver.disconnect();
    }, []);

    const demoVars: IndustryDemoInlineVars = {
        "--industry-demo-scale": `${scale}`,
    };

    return (
        <div
            ref={shellRef}
            id="industry-product-demo"
            className={styles.shell}
            style={demoVars}
        >
            <div
                id="industry-demo-scaled-viewport"
                className={styles.scaledViewport}
            >
                <IndustryDemoHeader persona={industry.persona}/>
                <div
                    id="industry-demo-app-body"
                    className={styles.body}
                >
                    <IndustryExchangeList exchanges={industry.exchanges}/>
                    <IndustryDocumentView industry={industry}/>
                </div>
            </div>
        </div>
    );
}
