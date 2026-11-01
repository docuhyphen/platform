import {usePreviewWatermarkStyles} from "./PreviewWatermarkStyles.tsx";

interface PreviewWatermarkProps
{
    watermarkText: string;
}

const PreviewWatermark = ({watermarkText}: PreviewWatermarkProps) =>
{
    const styles = usePreviewWatermarkStyles();

    return (
        <div id={"exchange-document-previewer-div-10"}
             aria-hidden
             className={styles.watermarkOverlay}>
            <div id={"exchange-document-previewer-div-11"}
                 className={styles.watermarkText}>
                {watermarkText} · CONFIDENTIAL
            </div>
        </div>
    );
};

export default PreviewWatermark;
